package io.github.nikolaynn.docengine.api;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Generates documents from office templates. An engine is an application-scoped
 * singleton: create one, share it, close it on application shutdown.
 *
 * <p><strong>Thread safety.</strong> An engine must be safe for concurrent use —
 * the bundled implementation is, and any custom SPI component plugged into an
 * engine must be as well, since one instance serves concurrent generations.
 */
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
