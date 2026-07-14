package io.github.nikolaynn.docengine.internal.libreoffice;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibreOfficeConverterTest {

    @Test
    void supportsXlsxToPdfOnly() {
        var c = new LibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(60), null);
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).isTrue();
        assertThat(c.supports(DocumentFormat.PDF, DocumentFormat.XLSX)).isFalse();
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.XLSX)).isFalse();
    }

    @Test
    void missingExecutableMapsToConversionException(@TempDir Path tmp) throws Exception {
        Path input = Files.createTempFile(tmp, "in", ".xlsx");
        Files.writeString(input, "stub");
        Path bogusExe = tmp.resolve("definitely-not-soffice");
        var c = new LibreOfficeConverter(bogusExe, Duration.ofSeconds(5), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(5), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class);
    }

    @Test
    void rejectsUnsupportedConversionPair(@TempDir Path tmp) throws Exception {
        Path input = Files.createTempFile(tmp, "in", ".pdf");
        Files.writeString(input, "stub");
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        var c = new LibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(5), tmp);

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.PDF, DocumentFormat.XLSX,
                new ConvertContext(Duration.ofSeconds(5), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("unsupported");
    }

    @Test
    void passesUniqueUserInstallationProfilePerInvocation(@TempDir Path tmp) throws Exception {
        Path argsFile = tmp.resolve("args.txt");
        Path exe = createFakeSoffice(tmp, Map.of(
            "fake.argsFile", argsFile.toString(),
            "fake.createOutput", "true"));
        var c = new LibreOfficeConverter(exe, Duration.ofSeconds(30), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        c.convert(stubInput(tmp, "first"), DocumentFormat.XLSX, DocumentFormat.PDF,
            new ConvertContext(Duration.ofSeconds(30), tfm, "tpl"));
        c.convert(stubInput(tmp, "second"), DocumentFormat.XLSX, DocumentFormat.PDF,
            new ConvertContext(Duration.ofSeconds(30), tfm, "tpl"));

        List<String> profiles = Files.readAllLines(argsFile).stream()
            .filter(l -> l.startsWith("-env:UserInstallation="))
            .collect(Collectors.toList());
        assertThat(profiles)
            .as("each soffice invocation must get its own -env:UserInstallation profile")
            .hasSize(2);
        assertThat(profiles.get(0)).contains("file:");
        assertThat(profiles.get(0)).isNotEqualTo(profiles.get(1));
    }

    @Test
    void killsWholeProcessTreeOnTimeout(@TempDir Path tmp) throws Exception {
        // Forcibly-killed processes release their file/CWD handles asynchronously on
        // Windows, so everything they touch lives in a self-managed dir outside
        // @TempDir (whose instant cleanup would race those handles), and the converter
        // gets no workingDir so the children don't inherit a locked CWD.
        Path side = Files.createTempDirectory("doc-engine-kill-test-");
        long childPid = -1;
        try {
            Path pidFile = side.resolve("child.pid");
            Path exe = createFakeSoffice(side, Map.of(
                "fake.childPidFile", pidFile.toString(),
                "fake.sleepMs", "60000"));
            var c = new LibreOfficeConverter(exe, Duration.ofSeconds(4), null);
            TempFileManager tfm = new DefaultTempFileManager(tmp, false);

            assertThatThrownBy(() -> c.convert(stubInput(tmp, "hang"), DocumentFormat.XLSX, DocumentFormat.PDF,
                    new ConvertContext(Duration.ofSeconds(4), tfm, "tpl")))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("timeout");

            final long pid = Long.parseLong(waitForContent(pidFile, Duration.ofSeconds(10)).trim());
            childPid = pid;
            boolean childDead = waitUntil(
                () -> ProcessHandle.of(pid).map(h -> !h.isAlive()).orElse(true),
                Duration.ofSeconds(5));
            assertThat(childDead)
                .as("descendant process (pid %d) must be killed together with soffice", pid)
                .isTrue();
        } finally {
            if (childPid > 0) {
                ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
            }
            bestEffortDeleteRecursively(side);
        }
    }

    @Test
    void drainsStdoutSoLargeOutputDoesNotDeadlock(@TempDir Path tmp) throws Exception {
        Path exe = createFakeSoffice(tmp, Map.of(
            "fake.stdoutBytes", "2000000",
            "fake.createOutput", "true"));
        var c = new LibreOfficeConverter(exe, Duration.ofSeconds(15), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        Path result = c.convert(stubInput(tmp, "chatty"), DocumentFormat.XLSX, DocumentFormat.PDF,
            new ConvertContext(Duration.ofSeconds(15), tfm, "tpl"));

        assertThat(result).exists();
        assertThat(Files.readString(result)).startsWith("%PDF");
    }

    @Test
    void includesLateStderrInFailureMessage(@TempDir Path tmp) throws Exception {
        Path exe = createFakeSoffice(tmp, Map.of(
            "fake.sleepMs", "1500",
            "fake.stderrText", "BOOM-DIAGNOSTIC",
            "fake.exit", "7"));
        var c = new LibreOfficeConverter(exe, Duration.ofSeconds(30), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        assertThatThrownBy(() -> c.convert(stubInput(tmp, "boom"), DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(30), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("exited with code 7")
            .hasMessageContaining("BOOM-DIAGNOSTIC");
    }

    @Test
    void decodesStderrWithPlatformNativeEncoding(@TempDir Path tmp) throws Exception {
        Path exe = createFakeSoffice(tmp, Map.of(
            "fake.stderrNonAscii", "true",
            "fake.exit", "9"));
        var c = new LibreOfficeConverter(exe, Duration.ofSeconds(30), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        assertThatThrownBy(() -> c.convert(stubInput(tmp, "enc"), DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(30), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("exited with code 9")
            .hasMessageContaining("STDERR-DIAGNOSE-ÄÖÜ");
    }

    private static Path stubInput(Path dir, String name) throws IOException {
        Path input = dir.resolve(name + ".xlsx");
        Files.writeString(input, "stub");
        return input;
    }

    /** Generates a cmd/sh wrapper that runs {@link FakeSoffice} in a fresh JVM with the given -D properties. */
    private static Path createFakeSoffice(Path dir, Map<String, String> props) throws IOException {
        String java = FakeSoffice.javaExecutable();
        String classpath = System.getProperty("java.class.path");
        String propArgs = props.entrySet().stream()
            .map(e -> "\"-D" + e.getKey() + "=" + e.getValue() + "\"")
            .collect(Collectors.joining(" "));
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        if (windows) {
            Path script = dir.resolve("fake-soffice.cmd");
            Files.writeString(script, "@echo off\r\n"
                + "\"" + java + "\" " + propArgs + " -cp \"" + classpath + "\" "
                + FakeSoffice.class.getName() + " %*\r\n"
                + "exit /b %errorlevel%\r\n");
            return script;
        }
        Path script = dir.resolve("fake-soffice.sh");
        Files.writeString(script, "#!/bin/sh\n"
            + "exec \"" + java + "\" " + propArgs + " -cp \"" + classpath + "\" "
            + FakeSoffice.class.getName() + " \"$@\"\n");
        if (!script.toFile().setExecutable(true)) {
            throw new IOException("cannot mark " + script + " executable");
        }
        return script;
    }

    private static String waitForContent(Path file, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)) {
                String content = Files.readString(file);
                if (!content.isBlank()) return content;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("file " + file + " did not appear within " + timeout);
    }

    private static boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private static void bestEffortDeleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // killed processes may briefly hold handles; a leftover in the OS
                    // temp dir is acceptable
                }
            });
        } catch (IOException ignored) {
        }
    }
}
