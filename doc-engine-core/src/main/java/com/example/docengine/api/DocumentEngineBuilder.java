package com.example.docengine.api;

import com.example.docengine.internal.DefaultDocumentEngine;
import com.example.docengine.internal.resolver.InputStreamTemplateResolver;
import com.example.docengine.internal.validator.NoopTemplateValidator;
import com.example.docengine.spi.DocumentConverter;
import com.example.docengine.spi.TempFileManager;
import com.example.docengine.spi.TemplateEngine;
import com.example.docengine.spi.TemplateResolver;
import com.example.docengine.spi.TemplateValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DocumentEngineBuilder {

    private TempFileManager tempFileManager;
    private TemplateResolver templateResolver;
    private TemplateValidator templateValidator;
    private final List<TemplateEngine> templateEngines = new ArrayList<>();
    private final List<DocumentConverter> converters = new ArrayList<>();

    private DocumentEngineBuilder() {}

    public static DocumentEngineBuilder create() { return new DocumentEngineBuilder(); }

    public DocumentEngineBuilder tempFileManager(TempFileManager tfm) {
        this.tempFileManager = Objects.requireNonNull(tfm); return this;
    }
    public DocumentEngineBuilder templateResolver(TemplateResolver tr) {
        this.templateResolver = Objects.requireNonNull(tr); return this;
    }
    public DocumentEngineBuilder templateValidator(TemplateValidator tv) {
        this.templateValidator = Objects.requireNonNull(tv); return this;
    }
    public DocumentEngineBuilder addTemplateEngine(TemplateEngine te) {
        templateEngines.add(Objects.requireNonNull(te)); return this;
    }
    public DocumentEngineBuilder addConverter(DocumentConverter dc) {
        converters.add(Objects.requireNonNull(dc)); return this;
    }

    public DocumentEngine build() {
        if (templateEngines.isEmpty()) {
            throw new IllegalStateException("at least one templateEngine is required");
        }
        if (tempFileManager == null) {
            throw new IllegalStateException("tempFileManager is required");
        }
        TemplateResolver tr = templateResolver != null ? templateResolver : new InputStreamTemplateResolver();
        TemplateValidator tv = templateValidator != null ? templateValidator : new NoopTemplateValidator();
        return new DefaultDocumentEngine(templateEngines, converters, tr, tv, tempFileManager);
    }
}
