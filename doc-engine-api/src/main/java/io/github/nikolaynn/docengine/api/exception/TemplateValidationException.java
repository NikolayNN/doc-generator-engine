package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class TemplateValidationException extends DocumentGenerationException {
    public TemplateValidationException(String templateHint, DocumentFormat sourceFormat, String message) {
        super(templateHint, sourceFormat, null, message, null);
    }
}
