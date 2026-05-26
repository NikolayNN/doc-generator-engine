package com.example.docengine;

import com.example.docengine.api.DocumentEngine;
import com.example.docengine.api.DocumentEngineBuilder;
import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.GenerationOptions;
import com.example.docengine.api.GenerationRequest;
import com.example.docengine.api.TemplateReference;
import com.example.docengine.internal.jxls.JxlsTemplateEngine;
import com.example.docengine.internal.libreoffice.LibreOfficeConverter;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.support.TemplateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndTest {

    @Test
    void xlsxRoundTripWithBuilder(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .build();
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "tpl"),
            Map.of("greeting", "Hi", "name", "World"),
            DocumentFormat.XLSX,
            GenerationOptions.defaults());
        var result = engine.generate(req);
        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(result.fileName()).endsWith(".xlsx");
        assertThat(result.content()).isNotEmpty();
    }

    @EnabledIf("com.example.docengine.internal.libreoffice.LibreOfficeConverterIT#sofficeAvailable")
    @Test
    void pdfRoundTripWithBuilder(@TempDir Path tmp) throws IOException {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .addConverter(new LibreOfficeConverter(null, Duration.ofSeconds(60), tmp))
            .build();
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "tpl"),
            Map.of("greeting", "Hi", "name", "World"),
            DocumentFormat.PDF,
            GenerationOptions.defaults());
        var result = engine.generate(req);
        assertThat(result.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(result.fileName()).endsWith(".pdf");
        assertThat(result.content()).isNotEmpty();
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }
}
