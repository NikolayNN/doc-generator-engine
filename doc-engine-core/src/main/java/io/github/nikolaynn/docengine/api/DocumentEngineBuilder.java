package io.github.nikolaynn.docengine.api;

import io.github.nikolaynn.docengine.internal.DefaultDocumentEngine;
import io.github.nikolaynn.docengine.internal.jxls.JxlsTemplateEngine;
import io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverter;
import io.github.nikolaynn.docengine.internal.resolver.InputStreamTemplateResolver;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.internal.validator.NoopTemplateValidator;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for a plain-Java {@link DocumentEngine}. Start with
 * {@link #create()}, add components (or {@link #withDefaults()}), then
 * {@link #build()}. At least one template engine and a temp-file manager are
 * required; a resolver and validator default to no-op implementations.
 */
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

    /**
     * Full default stack: JXLS engine, LibreOffice converter (soffice from PATH)
     * and a system-temp file manager with cleanup on JVM shutdown. The converter
     * is only exercised when a conversion is requested, so a missing soffice
     * does not affect XLSX-to-XLSX generation.
     */
    public DocumentEngineBuilder withDefaults() {
        return withJxlsEngine()
            .withLibreOfficeConverter()
            .withDefaultTempFileManager(null, true);
    }

    public DocumentEngineBuilder withJxlsEngine() {
        return addTemplateEngine(new JxlsTemplateEngine());
    }

    /** LibreOffice converter with soffice from PATH and the default timeout. */
    public DocumentEngineBuilder withLibreOfficeConverter() {
        return withLibreOfficeConverter(null, null, null);
    }

    public DocumentEngineBuilder withLibreOfficeConverter(Path executable,
                                                          Duration defaultTimeout,
                                                          Path workingDir) {
        return addConverter(new LibreOfficeConverter(executable, defaultTimeout, workingDir));
    }

    /** @param rootDir temp root directory; {@code null} means the system temp dir */
    public DocumentEngineBuilder withDefaultTempFileManager(Path rootDir, boolean cleanupOnShutdown) {
        return tempFileManager(new DefaultTempFileManager(rootDir, cleanupOnShutdown));
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
