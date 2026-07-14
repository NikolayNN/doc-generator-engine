package com.example.docengine.internal.jxls;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.exception.TemplateRenderingException;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.spi.RenderContext;
import com.example.docengine.spi.ResolvedTemplate;
import com.example.docengine.spi.TempFileManager;
import com.example.docengine.support.TemplateFixtures;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JxlsTemplateEngineTest {

    private JxlsTemplateEngine engine;
    private TempFileManager tfm;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        engine = new JxlsTemplateEngine();
        tfm = new DefaultTempFileManager(tmp, false);
    }

    @Test
    void supportsXlsx() {
        assertThat(engine.supports(DocumentFormat.XLSX)).isTrue();
        assertThat(engine.supports(DocumentFormat.PDF)).isFalse();
    }

    @Test
    void rendersScalarFields() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.simpleFields(),
            DocumentFormat.XLSX, "simple-fields");
        Map<String, Object> data = Map.of("greeting", "Hello", "name", "World");

        Path out = engine.render(template, data, ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
            Sheet sh = wb.getSheetAt(0);
            assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Hello");
            assertThat(sh.getRow(0).getCell(1).getStringCellValue()).isEqualTo("World");
        }
    }

    @Test
    void rendersTableWithJxEach() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.tableEach(),
            DocumentFormat.XLSX, "table-each");
        Map<String, Object> data = Map.of("items", List.of(
            Map.of("name", "A", "qty", 2, "price", new BigDecimal("100")),
            Map.of("name", "B", "qty", 3, "price", new BigDecimal("50"))
        ));

        Path out = engine.render(template, data, ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
            Sheet sh = wb.getSheetAt(0);
            assertThat(sh.getRow(1).getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(sh.getRow(2).getCell(0).getStringCellValue()).isEqualTo("B");
            assertThat(sh.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2.0);
            assertThat(sh.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(3.0);
        }
    }

    @Test
    void preservesFormulasForRecalculationOnOpen() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.formulas(),
            DocumentFormat.XLSX, "formulas");
        Map<String, Object> data = Map.of("a", 10, "b", 5);

        Path out = engine.render(template, data, ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
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

    @Test
    void rendersFormulasThatPoiCannotEvaluate() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.unimplementedFunctionFormula(),
            DocumentFormat.XLSX, "phonetic");

        Path out = engine.render(template, Map.of("a", "text"), ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
            var formulaCell = wb.getSheetAt(0).getRow(1).getCell(0);
            assertThat(formulaCell.getCellFormula()).contains("PHONETIC");
            assertThat(wb.getForceFormulaRecalculation()).isTrue();
        }
    }

    @Test
    void deletesTempFileWhenRenderingFails(@TempDir Path tmp) throws Exception {
        Path bogus = tmp.resolve("no-such-dir").resolve("out.xlsx");
        List<Path> deleted = new ArrayList<>();
        TempFileManager recording = new TempFileManager() {
            @Override public Path createTempFile(String prefix, String suffix) { return bogus; }
            @Override public void delete(Path path) { deleted.add(path); }
        };
        var template = new ResolvedTemplate(TemplateFixtures.simpleFields(),
            DocumentFormat.XLSX, "leak");

        assertThatThrownBy(() -> engine.render(template, Map.of("greeting", "g", "name", "n"),
                new RenderContext(null, null, Map.of(), recording, "test")))
            .isInstanceOf(TemplateRenderingException.class);

        assertThat(deleted)
            .as("temp file created by render must be deleted when rendering fails")
            .contains(bogus);
    }

    @Test
    void rejectsUnsupportedSourceFormat() {
        var template = new ResolvedTemplate(new byte[]{0}, DocumentFormat.PDF, "x");
        assertThatThrownBy(() -> engine.render(template, Map.of(), ctx()))
            .isInstanceOf(TemplateRenderingException.class)
            .hasMessageContaining("PDF");
    }

    @Test
    void mapsJxlsFailureToTemplateRenderingException() {
        var template = new ResolvedTemplate(new byte[]{0, 1, 2, 3}, DocumentFormat.XLSX, "corrupt");
        assertThatThrownBy(() -> engine.render(template, Map.of(), ctx()))
            .isInstanceOf(TemplateRenderingException.class);
    }

    private RenderContext ctx() {
        return new RenderContext(null, null, Map.of(), tfm, "test");
    }
}
