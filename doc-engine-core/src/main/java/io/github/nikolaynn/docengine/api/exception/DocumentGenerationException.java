package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public abstract class DocumentGenerationException extends RuntimeException {

    private final String templateHint;
    private final DocumentFormat sourceFormat;
    private final DocumentFormat targetFormat;

    protected DocumentGenerationException(String templateHint,
                                          DocumentFormat sourceFormat,
                                          DocumentFormat targetFormat,
                                          String message,
                                          Throwable cause) {
        super(buildMessage(message, templateHint, sourceFormat, targetFormat), cause);
        this.templateHint = templateHint;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    public String getTemplateHint() { return templateHint; }
    public DocumentFormat getSourceFormat() { return sourceFormat; }
    public DocumentFormat getTargetFormat() { return targetFormat; }

    private static String buildMessage(String message,
                                       String templateHint,
                                       DocumentFormat sourceFormat,
                                       DocumentFormat targetFormat) {
        StringBuilder sb = new StringBuilder();
        if (message != null) sb.append(message);
        sb.append(" [template=").append(templateHint == null ? "<unknown>" : templateHint);
        if (sourceFormat != null) sb.append(", source=").append(sourceFormat);
        if (targetFormat != null) sb.append(", target=").append(targetFormat);
        sb.append("]");
        return sb.toString();
    }
}
