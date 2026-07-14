package io.github.nikolaynn.docengine.internal.libreoffice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fake soffice executable used by LibreOfficeConverterTest. Launched as a separate JVM
 * via a generated cmd/sh wrapper script; behavior is controlled by -Dfake.* system
 * properties baked into the wrapper:
 *
 *   fake.argsFile     - append received args (one per line, "----" terminator) to this file
 *   fake.childPidFile - spawn a detached child JVM that writes its pid here and sleeps 60s
 *   fake.stdoutBytes  - write this many bytes to stdout before doing anything else
 *   fake.sleepMs      - sleep this long before writing stderr / creating output
 *   fake.stderrText   - line to print to stderr
 *   fake.createOutput - parse --outdir from args and create "&lt;input-stem&gt;.pdf" there
 *   fake.exit         - exit code (default 0)
 */
public final class FakeSoffice {

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--sleep-child".equals(args[0])) {
            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
            Thread.sleep(60_000);
            return;
        }

        String argsFile = System.getProperty("fake.argsFile");
        if (argsFile != null) {
            List<String> lines = new ArrayList<>(Arrays.asList(args));
            lines.add("----");
            Files.write(Path.of(argsFile), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        String childPidFile = System.getProperty("fake.childPidFile");
        if (childPidFile != null) {
            new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
                FakeSoffice.class.getName(), "--sleep-child", childPidFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        }

        int stdoutBytes = Integer.getInteger("fake.stdoutBytes", 0);
        if (stdoutBytes > 0) {
            byte[] chunk = new byte[8192];
            Arrays.fill(chunk, (byte) 'x');
            int written = 0;
            while (written < stdoutBytes) {
                int n = Math.min(chunk.length, stdoutBytes - written);
                System.out.write(chunk, 0, n);
                written += n;
            }
            System.out.flush();
        }

        long sleepMs = Long.getLong("fake.sleepMs", 0L);
        if (sleepMs > 0) {
            Thread.sleep(sleepMs);
        }

        String stderrText = System.getProperty("fake.stderrText");
        if (stderrText != null) {
            System.err.println(stderrText);
        }

        if (Boolean.getBoolean("fake.stderrNonAscii")) {
            // encoded by this child JVM's platform charset (Cp1252 on western Windows),
            // NOT UTF-8 — exercises the parent's stderr decoding
            System.err.println("STDERR-DIAGNOSE-ÄÖÜ");
        }

        if (Boolean.getBoolean("fake.createOutput")) {
            Path outDir = null;
            for (int i = 0; i < args.length - 1; i++) {
                if ("--outdir".equals(args[i])) {
                    outDir = Path.of(args[i + 1]);
                }
            }
            Path input = Path.of(args[args.length - 1]);
            String base = input.getFileName().toString();
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            Files.writeString(outDir.resolve(stem + ".pdf"), "%PDF-fake");
        }

        System.exit(Integer.getInteger("fake.exit", 0));
    }

    static String javaExecutable() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }
}
