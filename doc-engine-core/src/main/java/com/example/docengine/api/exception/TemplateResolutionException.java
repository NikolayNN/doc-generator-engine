package com.example.docengine.api.exception;

import com.example.docengine.api.DocumentFormat;

public class TemplateResolutionException extends DocumentGenerationException {
    public TemplateResolutionException(String templateHint, DocumentFormat sourceFormat,
                                       String message, Throwable cause) {
        super(templateHint, sourceFormat, null, message, cause);
    }
}
