package io.github.nikolaynn.docengine.api;

/**
 * Result metadata for streaming generation variants, where the document bytes
 * go to a caller-supplied sink instead of {@link GenerationResult#content()}.
 */
public record GenerationMetadata(String fileName, String mimeType, DocumentFormat format) {
}
