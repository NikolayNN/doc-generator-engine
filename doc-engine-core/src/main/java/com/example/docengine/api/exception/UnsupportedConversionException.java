package com.example.docengine.api.exception;

import com.example.docengine.api.DocumentFormat;

public class UnsupportedConversionException extends DocumentGenerationException {
    public UnsupportedConversionException(String templateHint,
                                          DocumentFormat sourceFormat,
                                          DocumentFormat targetFormat) {
        super(templateHint, sourceFormat, targetFormat,
              "no DocumentConverter supports conversion", null);
    }
}
