package io.github.nikolaynn.docengine.internal;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationMetadata;
import io.github.nikolaynn.docengine.api.GenerationOptions;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.GenerationResult;
import io.github.nikolaynn.docengine.api.exception.InvalidGenerationRequestException;
import io.github.nikolaynn.docengine.api.exception.TempFileException;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        if (this.templateEngines.isEmpty()) {
            // fail at construction (e.g. Spring context refresh), not on the first generate()
            throw new IllegalStateException("at least one templateEngine is required");
        }
    }

    @Override
    public void close() {
        for (DocumentConverter converter : converters) {
            try {
                converter.close();
            } catch (Exception e) {
                log.warn("failed to close converter {}: {}",
                    converter.getClass().getSimpleName(), e.getMessage());
            }
        }
        tempFiles.close();
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        return generateInternal(request, (output, meta) ->
            new GenerationResult(meta.fileName(), meta.mimeType(), meta.format(),
                Files.readAllBytes(output)));
    }

    @Override
    public GenerationMetadata generateTo(GenerationRequest request, OutputStream out) {
        Objects.requireNonNull(out, "out");
        return generateInternal(request, (output, meta) -> {
            Files.copy(output, out);
            out.flush();
            return meta;
        });
    }

    @Override
    public GenerationMetadata generateToFile(GenerationRequest request, Path target) {
        Objects.requireNonNull(target, "target");
        return generateInternal(request, (output, meta) -> {
            Files.move(output, target, StandardCopyOption.REPLACE_EXISTING);
            return meta;
        });
    }

    /** Delivers the produced document file to its final destination. */
    @FunctionalInterface
    private interface OutputDelivery<R> {
        R deliver(Path output, GenerationMetadata metadata) throws IOException;
    }

    private <R> R generateInternal(GenerationRequest request, OutputDelivery<R> delivery) {
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

            String fileName = buildFileName(opts.fileNameHint(), request.template().hint(), target);
            GenerationMetadata meta = new GenerationMetadata(fileName, target.mimeType(), target);
            try {
                return delivery.deliver(output, meta);
            } catch (IOException e) {
                throw new TempFileException(request.template().hint(), null, null,
                    "failed to deliver produced document", e);
            }
        } finally {
            tempFiles.delete(rendered);
            tempFiles.delete(converted);
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
