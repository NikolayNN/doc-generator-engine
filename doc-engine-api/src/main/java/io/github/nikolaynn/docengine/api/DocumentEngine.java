package io.github.nikolaynn.docengine.api;

import java.io.OutputStream;
import java.nio.file.Path;

public interface DocumentEngine extends AutoCloseable {

    /** Generates the document fully buffered in memory. */
    GenerationResult generate(GenerationRequest request);

    /**
     * Generates the document and streams it into {@code out} without buffering
     * the whole content in memory. The stream is flushed but not closed —
     * it belongs to the caller.
     */
    GenerationMetadata generateTo(GenerationRequest request, OutputStream out);

    /**
     * Generates the document into {@code target} (overwriting an existing file)
     * without buffering the whole content in memory. The caller owns the file.
     */
    GenerationMetadata generateToFile(GenerationRequest request, Path target);

    /** Releases engine resources (temp files etc.); no-op by default, must be idempotent. */
    @Override
    default void close() {}
}
