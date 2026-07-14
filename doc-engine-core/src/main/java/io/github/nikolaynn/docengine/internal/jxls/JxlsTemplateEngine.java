package io.github.nikolaynn.docengine.internal.jxls;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.TemplateRenderingException;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import org.jxls.common.Context;
import org.jxls.transform.Transformer;
import org.jxls.transform.poi.PoiTransformer;
import org.jxls.util.JxlsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        try (InputStream in = new ByteArrayInputStream(template.bytes());
             OutputStream os = new BufferedOutputStream(Files.newOutputStream(out))) {
            Context jxlsCtx = new Context();
            data.forEach(jxlsCtx::putVar);

            JxlsHelper helper = JxlsHelper.getInstance();
            Transformer transformer = helper.createTransformer(in, os);
            if (transformer instanceof PoiTransformer poi) {
                // formula recalculation is delegated to the opening application
                // (Excel / LibreOffice); POI-side evaluation would re-parse the whole
                // workbook and fails on functions POI does not implement
                poi.getWorkbook().setForceFormulaRecalculation(true);
            }
            helper.processTemplate(jxlsCtx, transformer);

            log.debug("rendered template {} to {}", template.hint(), out);
            return out;
        } catch (IOException | RuntimeException e) {
            ctx.tempFileManager().delete(out);
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "JXLS failed to render template", e);
        }
    }
}
