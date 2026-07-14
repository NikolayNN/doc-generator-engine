package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class TemplateResolutionException extends DocumentGenerationException {
    public TemplateResolutionException(String templateHint, DocumentFormat sourceFormat,
                                       String message, Throwable cause) {
        super(templateHint, sourceFormat, null, message, cause);
    }
}
