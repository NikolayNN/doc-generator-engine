package io.github.nikolaynn.docengine.api;

public interface DocumentEngine {
    GenerationResult generate(GenerationRequest request);
}
