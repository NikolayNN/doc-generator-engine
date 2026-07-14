package io.github.nikolaynn.docengine.api;

public interface DocumentEngine extends AutoCloseable {
    GenerationResult generate(GenerationRequest request);

    /** Releases engine resources (temp files etc.); no-op by default, must be idempotent. */
    @Override
    default void close() {}
}
