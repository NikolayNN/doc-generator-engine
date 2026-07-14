package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class UnsupportedConversionException extends DocumentGenerationException {
    public UnsupportedConversionException(String templateHint,
                                          DocumentFormat sourceFormat,
                                          DocumentFormat targetFormat) {
        super(templateHint, sourceFormat, targetFormat,
              "no DocumentConverter supports conversion", null);
    }
}
