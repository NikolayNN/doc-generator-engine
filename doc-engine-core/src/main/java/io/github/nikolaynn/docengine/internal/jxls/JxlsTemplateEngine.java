package io.github.nikolaynn.docengine.internal.jxls;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.TemplateRenderingException;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import org.jxls.transform.poi.JxlsPoiTemplateFillerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class JxlsTemplateEngine implements TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(JxlsTemplateEngine.class);

    @Override
    public boolean supports(DocumentFormat sourceFormat) {
        return sourceFormat == DocumentFormat.XLSX;
    }

    @Override
    public Path render(ResolvedTemplate template, Map<String, Object> data, RenderContext ctx) {
        if (template.sourceFormat() != DocumentFormat.XLSX) {
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "unsupported source format: " + template.sourceFormat(), null);
        }

        Path out = ctx.tempFileManager().createTempFile("doc-engine-", ".xlsx");
        try (InputStream in = new ByteArrayInputStream(template.bytes())) {
            JxlsPoiTemplateFillerBuilder.newInstance()
                .withTemplate(in)
                // formula recalculation is delegated to the opening application
                // (Excel / LibreOffice); POI-side evaluation would re-parse the whole
                // workbook and fails on functions POI does not implement — and it is
                // ON by default in JXLS 3 (recalculateFormulasBeforeSaving)
                .withRecalculateFormulasBeforeSaving(false)
                .withRecalculateFormulasOnOpening(true)
                // the default PoiExceptionLogger only LOGS render errors and lets a
                // broken file through; the error model requires them to fail loudly
                .withExceptionThrower()
                // JXLS 3 uses the passed map as its variable scope and writes
                // jx:each run-vars into it; hand it a discardable, null-tolerant
                // copy so unmodifiable request data keeps working
                .buildAndFill(new LinkedHashMap<>(data), out.toFile());

            log.debug("rendered template {} to {}", template.hint(), out);
            return out;
        } catch (IOException | RuntimeException e) {
            ctx.tempFileManager().delete(out);
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "JXLS failed to render template", e);
        }
    }
}
