package io.github.nikolaynn.docengine.api;

import java.util.Objects;

/**
 * A fully buffered generation result.
 *
 * @param fileName suggested file name (with extension)
 * @param mimeType MIME type of {@link #content()}
 * @param format the produced document format
 * @param content the produced document bytes
 */
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
