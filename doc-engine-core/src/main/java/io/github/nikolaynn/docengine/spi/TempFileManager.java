package io.github.nikolaynn.docengine.spi;

import java.nio.file.Path;

public interface TempFileManager {
    Path createTempFile(String prefix, String suffix);
    void delete(Path path);
}
