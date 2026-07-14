package io.github.nikolaynn.docengine.internal;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationOptions;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.GenerationResult;
import io.github.nikolaynn.docengine.api.exception.InvalidGenerationRequestException;
import io.github.nikolaynn.docengine.api.exception.UnsupportedConversionException;
import io.github.nikolaynn.docengine.api.exception.UnsupportedTemplateFormatException;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class DefaultDocumentEngine implements DocumentEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentEngine.class);

    private final List<TemplateEngine> templateEngines;
    private final List<DocumentConverter> converters;
    private final TemplateResolver resolver;
    private final TemplateValidator validator;
    private final TempFileManager tempFiles;

    public DefaultDocumentEngine(List<TemplateEngine> templateEngines,
                                 List<DocumentConverter> converters,
                                 TemplateResolver resolver,
                                 TemplateValidator validator,
                                 TempFileManager tempFiles) {
        this.templateEngines = List.copyOf(Objects.requireNonNull(templateEngines, "templateEngines"));
        this.converters = List.copyOf(Objects.requireNonNull(converters, "converters"));
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.tempFiles = Objects.requireNonNull(tempFiles, "tempFiles");
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        if (request == null) {
            throw new InvalidGenerationRequestException("request must not be null");
        }
        DocumentFormat target = request.targetFormat();
        DocumentFormat source = request.template().sourceFormat();
        GenerationOptions opts = request.options();

        validator.validate(request.template());
        ResolvedTemplate resolved = resolver.resolve(request.template());

        TemplateEngine te = templateEngines.stream()
            .filter(e -> e.supports(source))
            .findFirst()
            .orElseThrow(() -> new UnsupportedTemplateFormatException(request.template().hint(), source));

        Path rendered = null;
        Path converted = null;
        try {
            RenderContext rctx = new RenderContext(opts.locale(), opts.timeout(),
                opts.engineHints(), tempFiles, request.template().hint());
            rendered = te.render(resolved, request.data(), rctx);

            Path output;
            if (source == target) {
                output = rendered;
            } else {
                DocumentConverter dc = converters.stream()
                    .filter(c -> c.supports(source, target))
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedConversionException(
                        request.template().hint(), source, target));
                ConvertContext cctx = new ConvertContext(opts.timeout(), tempFiles,
                    request.template().hint());
                converted = dc.convert(rendered, source, target, cctx);
                output = converted;
            }

            byte[] bytes = readAllBytes(output, request.template().hint());
            String fileName = buildFileName(opts.fileNameHint(), request.template().hint(), target);
            return new GenerationResult(fileName, target.mimeType(), target, bytes);
        } finally {
            tempFiles.delete(rendered);
            tempFiles.delete(converted);
        }
    }

    private static byte[] readAllBytes(Path file, String hint) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new io.github.nikolaynn.docengine.api.exception.TempFileException(
                hint, null, null, "failed to read produced document", e);
        }
    }

    private static String buildFileName(String hint, String templateHint, DocumentFormat target) {
        String ext = "." + target.extension();
        if (hint != null && !hint.isBlank()) {
            return hint.endsWith(ext) ? hint : hint + ext;
        }
        String base = (templateHint == null || templateHint.isBlank()) ? "document" : sanitize(templateHint);
        return base + "-" + Instant.now().toEpochMilli() + ext;
    }

    private static String sanitize(String hint) {
        String name = hint;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
