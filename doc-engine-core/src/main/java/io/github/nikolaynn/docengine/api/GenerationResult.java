package io.github.nikolaynn.docengine.api;

import java.util.Objects;

public record GenerationResult(
        String fileName,
        String mimeType,
        DocumentFormat format,
        byte[] content
) {
    public GenerationResult {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(content, "content");
    }
}
