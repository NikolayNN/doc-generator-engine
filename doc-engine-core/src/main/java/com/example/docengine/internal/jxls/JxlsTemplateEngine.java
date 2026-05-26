package com.example.docengine.internal.jxls;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.exception.TemplateRenderingException;
import com.example.docengine.spi.RenderContext;
import com.example.docengine.spi.ResolvedTemplate;
import com.example.docengine.spi.TemplateEngine;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

        byte[] rendered = renderToBytes(template, data);
        byte[] withFormulas = recalculateFormulas(rendered, template.hint());

        Path out = ctx.tempFileManager().createTempFile("doc-engine-", ".xlsx");
        try {
            Files.write(out, withFormulas);
            log.debug("rendered template {} to {}", template.hint(), out);
            return out;
        } catch (IOException e) {
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "failed to write rendered xlsx to temp file", e);
        }
    }

    private byte[] renderToBytes(ResolvedTemplate template, Map<String, Object> data) {
        try {
            Context jxlsCtx = new Context();
            data.forEach(jxlsCtx::putVar);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JxlsHelper.getInstance().processTemplate(
                new ByteArrayInputStream(template.bytes()), out, jxlsCtx);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "JXLS failed to render template", e);
        }
    }

    private byte[] recalculateFormulas(byte[] xlsx, String hint) {
        try (InputStream in = new ByteArrayInputStream(xlsx);
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();
            wb.setForceFormulaRecalculation(true);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new TemplateRenderingException(hint, DocumentFormat.XLSX, null,
                "failed to recalculate formulas in rendered workbook", e);
        }
    }
}
