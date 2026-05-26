package com.example.docengine.api.exception;

import com.example.docengine.api.DocumentFormat;

public class TemplateValidationException extends DocumentGenerationException {
    public TemplateValidationException(String templateHint, DocumentFormat sourceFormat, String message) {
        super(templateHint, sourceFormat, null, message, null);
    }
}
