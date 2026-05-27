package com.example.docengine;

import com.example.docengine.api.DocumentEngine;
import com.example.docengine.api.DocumentEngineBuilder;
import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.GenerationOptions;
import com.example.docengine.api.GenerationRequest;
import com.example.docengine.api.GenerationResult;
import com.example.docengine.api.TemplateReference;
import com.example.docengine.internal.jxls.JxlsTemplateEngine;
import com.example.docengine.internal.libreoffice.LibreOfficeConverter;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.support.TemplateFixtures;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndTest {

    @Test
    void xlsxRoundTripWithBuilder(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "tpl"),
            Map.of("greeting", "Hi", "name", "World"),
            DocumentFormat.XLSX,
            GenerationOptions.defaults());

        GenerationResult result = engine.generate(req);

        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(result.fileName()).endsWith(".xlsx");
        assertThat(result.mimeType()).isEqualTo(DocumentFormat.XLSX.mimeType());
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void xlsxSubstitutesScalarTokensInOutput(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "simple"),
            Map.of("greeting", "Hello", "name", "World"),
            DocumentFormat.XLSX,
            GenerationOptions.defaults());

        GenerationResult result = engine.generate(req);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            Sheet sh = wb.getSheetAt(0);
            assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Hello");
            assertThat(sh.getRow(0).getCell(1).getStringCellValue()).isEqualTo("World");
        }
    }

    @Test
    void xlsxExpandsTableWithJxEachAndSums(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        Map<String, Object> data = Map.of("items", List.of(
            Map.of("name", "Widget", "qty", 2, "price", new BigDecimal("100")),
            Map.of("name", "Gadget", "qty", 3, "price", new BigDecimal("50"))
        ));
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.tableEach(), DocumentFormat.XLSX, "table"),
            data,
            DocumentFormat.XLSX,
            GenerationOptions.defaults());

        GenerationResult result = engine.generate(req);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            Sheet sh = wb.getSheetAt(0);

            assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Name");
            assertThat(sh.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Qty");
            assertThat(sh.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Total");

            assertThat(sh.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Widget");
            assertThat(sh.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2.0);
            assertThat(sh.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Gadget");
            assertThat(sh.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(3.0);
        }
    }

    @Test
    void xlsxEvaluatesFormulasAfterSubstitution(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.formulas(), DocumentFormat.XLSX, "formulas"),
            Map.of("a", 10, "b", 5),
            DocumentFormat.XLSX,
            GenerationOptions.defaults());

        GenerationResult result = engine.generate(req);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            Sheet sh = wb.getSheetAt(0);
            var formulaCell = sh.getRow(2).getCell(0);
            assertThat(formulaCell.getCellFormula()).isEqualToIgnoringWhitespace("A1+A2");
            assertThat(formulaCell.getNumericCellValue()).isEqualTo(15.0);
        }
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

        GenerationResult result = engine.generate(req);

        assertThat(result.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(result.fileName()).endsWith(".pdf");
        assertThat(result.content()).isNotEmpty();
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.content()).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    private static DocumentEngine newXlsxEngine(Path tmp) {
        return DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .build();
    }
}
