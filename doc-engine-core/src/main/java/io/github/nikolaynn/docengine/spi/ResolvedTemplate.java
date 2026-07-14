package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;

import java.util.Objects;

public record ResolvedTemplate(byte[] bytes, DocumentFormat sourceFormat, String hint) {
    public ResolvedTemplate {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(sourceFormat, "sourceFormat");
    }
}
