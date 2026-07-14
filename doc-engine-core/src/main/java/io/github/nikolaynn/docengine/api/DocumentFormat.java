package io.github.nikolaynn.docengine.api;

public enum DocumentFormat {
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    private final String mimeType;
    private final String extension;

    DocumentFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() { return mimeType; }
    public String extension() { return extension; }
}
