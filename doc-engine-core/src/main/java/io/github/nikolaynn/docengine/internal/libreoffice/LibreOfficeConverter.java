package io.github.nikolaynn.docengine.internal.libreoffice;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public class LibreOfficeConverter implements DocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeConverter.class);
    private static final int STDERR_TRUNCATE = 2000;
    // grace period to wait for force-killed processes to actually exit and release
    // their handles (e.g. the soffice UserInstallation profile under outDir) before
    // the caller deletes that directory
    private static final Duration PROCESS_KILL_WAIT = Duration.ofSeconds(5);
    // Windows releases a dead process's file handles slightly AFTER it is reported
    // terminated, so a just-freed file can stay briefly undeletable; retry deletes
    // within this bound before giving up
    private static final Duration HANDLE_RELEASE_WAIT = Duration.ofSeconds(2);
    // native processes emit stderr in the platform charset (e.g. Cp1252/Cp1251 on
    // Windows), not necessarily UTF-8 or this JVM's default
    private static final Charset PROCESS_OUTPUT_CHARSET = processOutputCharset();

    private final Path executable;
    private final Duration defaultTimeout;
    private final Path workingDir;

    public LibreOfficeConverter(Path executable, Duration defaultTimeout, Path workingDir) {
        this.executable = executable;
        this.defaultTimeout = defaultTimeout == null ? Duration.ofSeconds(60) : defaultTimeout;
        this.workingDir = workingDir;
    }

    @Override
    public boolean supports(DocumentFormat from, DocumentFormat to) {
        return from == DocumentFormat.XLSX && to == DocumentFormat.PDF;
    }

    @Override
    public Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx) {
        if (!supports(from, to)) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "unsupported conversion " + from + "->" + to, null, false);
        }

        Path outDir = createOutDir(ctx);
        try {
            Duration timeout = ctx.timeout() == null ? defaultTimeout : ctx.timeout();

            ProcessBuilder pb = new ProcessBuilder(buildCommand(input, outDir))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectErrorStream(false);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "failed to start LibreOffice: " + e.getMessage(), e, false);
            }

            StringBuilder stderrBuf = new StringBuilder();
            Thread stderrReader = startStderrReader(process, stderrBuf);

            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                destroyProcessTree(process);
                Thread.currentThread().interrupt();
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "interrupted while waiting for LibreOffice", e, false);
            }

            if (!finished) {
                destroyProcessTree(process);
                throw DocumentConversionException.timeout(ctx.templateHint(), from, to, timeout);
            }

            int exit = process.exitValue();
            if (exit != 0) {
                String stderr = awaitStderr(stderrReader, stderrBuf);
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "LibreOffice exited with code " + exit + "; stderr=" + truncate(stderr),
                    null, false);
            }

            Path produced = findOutputFile(input, outDir, to);
            if (produced == null) {
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "LibreOffice exited 0 but no output file in " + outDir, null, false);
            }

            Path managed = ctx.tempFileManager().createTempFile("doc-engine-pdf-", "." + to.extension());
            try {
                Files.move(produced, managed, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // best-effort: clean managed file we just created
                ctx.tempFileManager().delete(managed);
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "failed to move LibreOffice output to managed temp file", e, false);
            }
            log.debug("converted {} -> {}", input, managed);
            return managed;
        } finally {
            deleteRecursively(outDir);
        }
    }

    private List<String> buildCommand(Path input, Path outDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable == null ? "soffice" : executable.toString());
        // isolated per-invocation profile: concurrent soffice instances must not share
        // the default user profile, or they hang/fail on its lock
        cmd.add("-env:UserInstallation=" + outDir.resolve("profile").toUri());
        cmd.add("--headless");
        cmd.add("--convert-to");
        cmd.add("pdf");
        cmd.add("--outdir");
        cmd.add(outDir.toString());
        cmd.add(input.toString());
        return cmd;
    }

    private Path createOutDir(ConvertContext ctx) {
        try {
            return workingDir == null
                ? Files.createTempDirectory("doc-engine-libo-")
                : Files.createTempDirectory(workingDir, "doc-engine-libo-");
        } catch (IOException e) {
            throw new DocumentConversionException(ctx.templateHint(),
                DocumentFormat.XLSX, DocumentFormat.PDF,
                "failed to create LibreOffice output directory", e, false);
        }
    }

    private static Path findOutputFile(Path input, Path outDir, DocumentFormat targetFormat) {
        String base = input.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        Path candidate = outDir.resolve(stem + "." + targetFormat.extension());
        if (Files.isRegularFile(candidate)) return candidate;
        try (Stream<Path> s = Files.list(outDir)) {
            return s.filter(p -> p.getFileName().toString().endsWith("." + targetFormat.extension()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Thread startStderrReader(Process p, StringBuilder sb) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), PROCESS_OUTPUT_CHARSET))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (sb) { sb.append(line).append('\n'); }
                }
            } catch (IOException ignored) {}
        }, "soffice-stderr");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String awaitStderr(Thread reader, StringBuilder sb) {
        // the process has already exited, so its stderr stream is at EOF; the join
        // bound only guards against a pathologically stuck reader
        try { reader.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        synchronized (sb) { return sb.toString(); }
    }

    private static void destroyProcessTree(Process process) {
        // snapshot descendants before killing the parent: soffice is a launcher whose
        // soffice.bin child does the work and gets re-parented once the launcher dies
        List<ProcessHandle> tree = new ArrayList<>();
        tree.add(process.toHandle());
        tree.addAll(process.toHandle().descendants().toList());
        tree.forEach(ProcessHandle::destroyForcibly);
        // destroyForcibly() is asynchronous; wait (bounded) for the processes to
        // actually terminate before returning. The caller deletes outDir right
        // after, and on Windows a still-alive soffice.bin keeps a handle on its
        // UserInstallation profile under outDir, so an immediate delete would
        // silently fail and leak the directory.
        awaitExit(tree);
    }

    private static void awaitExit(List<ProcessHandle> handles) {
        CompletableFuture<?>[] exits = handles.stream()
            .map(ProcessHandle::onExit)
            .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(PROCESS_KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            // best-effort: a process may outlive the grace period; the ensuing
            // temp-dir delete is itself best-effort and will just leave a leftover
            log.warn("killed LibreOffice process did not exit within {}", PROCESS_KILL_WAIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Charset processOutputCharset() {
        String enc = System.getProperty("native.encoding");
        if (enc != null) {
            try {
                return Charset.forName(enc);
            } catch (IllegalArgumentException ignored) {
                // fall through to the JVM default
            }
        }
        return Charset.defaultCharset();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= STDERR_TRUNCATE ? s : s.substring(0, STDERR_TRUNCATE) + "...";
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(LibreOfficeConverter::deleteWithRetry);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static void deleteWithRetry(Path p) {
        // a just-killed soffice releases its handles asynchronously on Windows, so a
        // file/dir under its profile can be briefly undeletable even after the
        // process is reported dead; retry within a short bound before giving up
        long deadline = System.nanoTime() + HANDLE_RELEASE_WAIT.toNanos();
        while (true) {
            try {
                Files.deleteIfExists(p);
                return;
            } catch (IOException e) {
                if (System.nanoTime() >= deadline) {
                    return; // best-effort: leave the leftover rather than block indefinitely
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
