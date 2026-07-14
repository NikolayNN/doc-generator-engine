package io.github.nikolaynn.docengine.spi;

import java.nio.file.Path;

public interface TempFileManager extends AutoCloseable {
    Path createTempFile(String prefix, String suffix);
    void delete(Path path);

    /** Releases resources and deletes tracked files; no-op by default, must be idempotent. */
    @Override
    default void close() {}
}
