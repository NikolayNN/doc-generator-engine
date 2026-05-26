package com.example.docengine.api.exception;

import com.example.docengine.api.DocumentFormat;

public class TemplateRenderingException extends DocumentGenerationException {
    public TemplateRenderingException(String templateHint,
                                      DocumentFormat sourceFormat,
                                      DocumentFormat targetFormat,
                                      String message,
                                      Throwable cause) {
        super(templateHint, sourceFormat, targetFormat, message, cause);
    }
}
