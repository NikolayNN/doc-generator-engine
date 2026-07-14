package io.github.nikolaynn.docengine;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.core.DocumentEngineBuilder;
import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationMetadata;
import io.github.nikolaynn.docengine.api.GenerationOptions;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.GenerationResult;
import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.internal.jxls.JxlsTemplateEngine;
import io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverter;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * (regenerate via {@code io.github.nikolaynn.docengine.support.SampleTemplateGenerator}).
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

            // Borders from the template row must survive on every generated row,
            // including the totals row that follows the jx:each block.
            for (int r = 0; r <= 3; r++) {
                Row row = sh.getRow(r);
                for (int c = 0; c <= 2; c++) {
                    assertCellHasThinBorders(row.getCell(c), "row " + r + " col " + c);
                }
            }
        }
    }

    private static void assertCellHasThinBorders(Cell cell, String where) {
        assertThat(cell).as("cell at %s", where).isNotNull();
        var style = cell.getCellStyle();
        assertThat(style.getBorderTop()).as("top border at %s", where).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderBottom()).as("bottom border at %s", where).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderLeft()).as("left border at %s", where).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderRight()).as("right border at %s", where).isEqualTo(BorderStyle.THIN);
    }

    @Test
    void xlsxPreservesFormulasForRecalculationOnOpen(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        GenerationResult result = engine.generate(request(FORMUL,
            Map.of("a", 10, "b", 5), DocumentFormat.XLSX));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            Sheet sh = wb.getSheetAt(0);
            var formulaCell = sh.getRow(2).getCell(0);
            assertThat(formulaCell.getCellFormula()).isEqualToIgnoringWhitespace("A1+A2");
            assertThat(wb.getForceFormulaRecalculation())
                .as("opening application must recalculate formulas")
                .isTrue();
            // correctness of the rendered formula, evaluated on the test side
            var value = wb.getCreationHelper().createFormulaEvaluator().evaluate(formulaCell);
            assertThat(value.getNumberValue()).isEqualTo(15.0);
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

    @EnabledIf("io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverterIT#sofficeAvailable")
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

    @Test
    void generateToStreamsDocumentToOutputStream(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        GenerationMetadata meta = engine.generateTo(request(SIMPLE,
            Map.of("greeting", "Hello", "name", "Stream"), DocumentFormat.XLSX), out);

        assertThat(meta.fileName()).endsWith(".xlsx");
        assertThat(meta.mimeType()).isEqualTo(DocumentFormat.XLSX.mimeType());
        assertThat(meta.format()).isEqualTo(DocumentFormat.XLSX);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Hello");
        }
        try (var leftovers = Files.list(tmp)) {
            assertThat(leftovers).as("no temp files may survive generateTo").isEmpty();
        }
    }

    @Test
    void generateToFileWritesDocumentToTargetPath(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = newXlsxEngine(tmp.resolve("work"));
        Path target = tmp.resolve("out/report.xlsx");
        Files.createDirectories(target.getParent());

        GenerationMetadata meta = engine.generateToFile(request(SIMPLE,
            Map.of("greeting", "Hi", "name", "File"), DocumentFormat.XLSX), target);

        assertThat(meta.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(target).exists();
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("File");
        }
        try (var leftovers = Files.list(tmp.resolve("work"))) {
            assertThat(leftovers).as("no temp files may survive generateToFile").isEmpty();
        }
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
