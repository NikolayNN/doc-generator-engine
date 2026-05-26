package com.example.docengine.api.exception;

import com.example.docengine.api.DocumentFormat;

public class UnsupportedTemplateFormatException extends DocumentGenerationException {
    public UnsupportedTemplateFormatException(String templateHint, DocumentFormat sourceFormat) {
        super(templateHint, sourceFormat, null,
              "no TemplateEngine supports source format", null);
    }
}
