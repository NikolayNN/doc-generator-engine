package io.github.nikolaynn.docengine.api;

/**
 * Metadata about a streamed generation (the bytes go to the caller's
 * {@code OutputStream}/file, so only the descriptors are returned).
 *
 * @param fileName suggested file name (with extension)
 * @param mimeType MIME type of the produced document
 * @param format the produced document format
 */
public record GenerationMetadata(String fileName, String mimeType, DocumentFormat format) {
}
