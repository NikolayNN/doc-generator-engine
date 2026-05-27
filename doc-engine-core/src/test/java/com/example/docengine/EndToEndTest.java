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
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the public {@link DocumentEngine} API.
 *
 * Templates are loaded from {@code src/test/resources/templates/*.xlsx}
 * (regenerate via {@code com.example.docengine.support.SampleTemplateGenerator}).
 * The {@link #dumpSamplesToTarget} test additionally writes a paired
 * input/output set to {@code target/e2e-samples/} for manual inspection.
 */
class EndToEndTest {

    private static final String SIMPLE = "/templates/simple-fields.xlsx";
    private static final String TABLE  = "/templates/table-each.xlsx";
    private static final String FORMUL = "/templates/formulas.xlsx";

    @Test
    void xlsxRoundTripWithBuilder(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        GenerationResult result = engine.generate(request(SIMPLE,
            Map.of("greeting", "Hi", "name", "World"), DocumentFormat.XLSX));

        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(result.fileName()).endsWith(".xlsx");
        assertThat(result.mimeType()).isEqualTo(DocumentFormat.XLSX.mimeType());
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void xlsxSubstitutesScalarTokensInOutput(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        GenerationResult result = engine.generate(request(SIMPLE,
            Map.of("greeting", "Hello", "name", "World"), DocumentFormat.XLSX));

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

        GenerationResult result = engine.generate(request(TABLE, data, DocumentFormat.XLSX));

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
        GenerationResult result = engine.generate(request(FORMUL,
            Map.of("a", 10, "b", 5), DocumentFormat.XLSX));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            Sheet sh = wb.getSheetAt(0);
            var formulaCell = sh.getRow(2).getCell(0);
            assertThat(formulaCell.getCellFormula()).isEqualToIgnoringWhitespace("A1+A2");
            assertThat(formulaCell.getNumericCellValue()).isEqualTo(15.0);
        }
    }

    /**
     * Writes input + output pairs to {@code target/e2e-samples/} so the
     * developer can open them in Excel/LibreOffice. Not asserting business
     * behavior here — that's covered by the dedicated tests above.
     */
    @Test
    void dumpSamplesToTarget(@TempDir Path tmp) throws Exception {
        Path out = Path.of("target/e2e-samples");
        Files.createDirectories(out);
        DocumentEngine engine = newXlsxEngine(tmp);

        copyResource(SIMPLE, out.resolve("simple-fields-INPUT.xlsx"));
        Files.write(out.resolve("simple-fields-OUTPUT.xlsx"),
            engine.generate(request(SIMPLE,
                Map.of("greeting", "Hello", "name", "World"),
                DocumentFormat.XLSX)).content());

        copyResource(TABLE, out.resolve("table-each-INPUT.xlsx"));
        Files.write(out.resolve("table-each-OUTPUT.xlsx"),
            engine.generate(request(TABLE,
                Map.of("items", List.of(
                    Map.of("name", "Widget", "qty", 2, "price", new BigDecimal("100")),
                    Map.of("name", "Gadget", "qty", 3, "price", new BigDecimal("50"))
                )),
                DocumentFormat.XLSX)).content());

        copyResource(FORMUL, out.resolve("formulas-INPUT.xlsx"));
        Files.write(out.resolve("formulas-OUTPUT.xlsx"),
            engine.generate(request(FORMUL,
                Map.of("a", 10, "b", 5),
                DocumentFormat.XLSX)).content());

        System.out.println("[e2e samples] " + out.toAbsolutePath());
    }

    @EnabledIf("com.example.docengine.internal.libreoffice.LibreOfficeConverterIT#sofficeAvailable")
    @Test
    void pdfRoundTripWithBuilder(@TempDir Path tmp) throws IOException {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .addConverter(new LibreOfficeConverter(null, Duration.ofSeconds(60), tmp))
            .build();

        GenerationResult result = engine.generate(request(SIMPLE,
            Map.of("greeting", "Hi", "name", "World"), DocumentFormat.PDF));

        assertThat(result.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(result.fileName()).endsWith(".pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.content()).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    private static GenerationRequest request(String resource, Map<String, Object> data,
                                             DocumentFormat target) throws IOException {
        return new GenerationRequest(
            new TemplateReference.BytesRef(loadResource(resource), DocumentFormat.XLSX, resource),
            data, target, GenerationOptions.defaults());
    }

    private static byte[] loadResource(String path) throws IOException {
        try (var in = Objects.requireNonNull(
                EndToEndTest.class.getResourceAsStream(path),
                "missing classpath resource: " + path)) {
            return in.readAllBytes();
        }
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (var in = Objects.requireNonNull(
                EndToEndTest.class.getResourceAsStream(resource),
                "missing classpath resource: " + resource)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static DocumentEngine newXlsxEngine(Path tmp) {
        return DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .build();
    }
}
