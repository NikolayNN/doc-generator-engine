package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import java.time.Duration;

public class DocumentConversionException extends DocumentGenerationException {

    private final boolean timeout;

    public DocumentConversionException(String templateHint,
                                       DocumentFormat sourceFormat,
                                       DocumentFormat targetFormat,
                                       String message,
                                       Throwable cause,
                                       boolean timeout) {
        super(templateHint, sourceFormat, targetFormat, message, cause);
        this.timeout = timeout;
    }

    public static DocumentConversionException timeout(String templateHint,
                                                      DocumentFormat sourceFormat,
                                                      DocumentFormat targetFormat,
                                                      Duration duration) {
        return new DocumentConversionException(
            templateHint, sourceFormat, targetFormat,
            "conversion timeout after " + duration.toSeconds() + "s",
            null, true);
    }

    public boolean isTimeout() { return timeout; }
}
