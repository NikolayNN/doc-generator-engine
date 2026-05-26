package com.example.docengine.internal.tempfile;

import com.example.docengine.api.exception.TempFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTempFileManagerTest {

    @Test
    void createsTempFileInGivenDirectoryWithExpectedSuffix(@TempDir Path tmp) throws Exception {
        var mgr = new DefaultTempFileManager(tmp, false);
        Path file = mgr.createTempFile("doc-", ".xlsx");

        assertThat(file).exists();
        assertThat(file.getParent()).isEqualTo(tmp);
        assertThat(file.getFileName().toString()).startsWith("doc-").endsWith(".xlsx");
    }

    @Test
    void deleteRemovesFile(@TempDir Path tmp) throws Exception {
        var mgr = new DefaultTempFileManager(tmp, false);
        Path file = mgr.createTempFile("a-", ".bin");
        assertThat(file).exists();

        mgr.delete(file);
        assertThat(file).doesNotExist();
    }

    @Test
    void deleteOfMissingFileIsSilent(@TempDir Path tmp) {
        var mgr = new DefaultTempFileManager(tmp, false);
        Path missing = tmp.resolve("nope.txt");
        mgr.delete(missing); // must not throw
    }

    @Test
    void deleteOfNullIsSilent(@TempDir Path tmp) {
        var mgr = new DefaultTempFileManager(tmp, false);
        mgr.delete(null);
    }

    @Test
    void nullRootDirFallsBackToSystemTemp() throws Exception {
        var mgr = new DefaultTempFileManager(null, false);
        Path file = mgr.createTempFile("b-", ".bin");
        try {
            assertThat(file).exists();
            assertThat(file.getParent()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void wrapsIoErrorInTempFileException() {
        var mgr = new DefaultTempFileManager(Path.of("/no/such/dir/should/not/exist/zzz"), false);
        assertThatThrownBy(() -> mgr.createTempFile("x", ".y"))
            .isInstanceOf(TempFileException.class);
    }
}
