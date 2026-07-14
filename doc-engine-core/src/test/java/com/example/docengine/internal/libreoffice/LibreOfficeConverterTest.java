package com.example.docengine.internal.libreoffice;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.exception.DocumentConversionException;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.spi.ConvertContext;
import com.example.docengine.spi.TempFileManager;
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
        Path pidFile = tmp.resolve("child.pid");
        Path exe = createFakeSoffice(tmp, Map.of(
            "fake.childPidFile", pidFile.toString(),
            "fake.sleepMs", "60000"));
        var c = new LibreOfficeConverter(exe, Duration.ofSeconds(4), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        assertThatThrownBy(() -> c.convert(stubInput(tmp, "hang"), DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(4), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("timeout");

        long childPid = Long.parseLong(waitForContent(pidFile, Duration.ofSeconds(10)).trim());
        try {
            boolean childDead = waitUntil(
                () -> ProcessHandle.of(childPid).map(h -> !h.isAlive()).orElse(true),
                Duration.ofSeconds(5));
            assertThat(childDead)
                .as("descendant process (pid %d) must be killed together with soffice", childPid)
                .isTrue();
        } finally {
            ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
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
}
