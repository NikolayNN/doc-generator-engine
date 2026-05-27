package com.example.docengine.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Re-generates the committed XLSX template fixtures under
 * {@code src/test/resources/templates/} from the programmatic
 * {@link TemplateFixtures} definitions.
 *
 * Run on demand:
 * <pre>
 *   mvn -pl doc-engine-core test -Dtest=SampleTemplateGenerator -Dregenerate.samples=true
 * </pre>
 */
class SampleTemplateGenerator {

    @Test
    @EnabledIfSystemProperty(named = "regenerate.samples", matches = "true")
    void regenerateClasspathTemplates() throws Exception {
        Path dir = Path.of("src/test/resources/templates");
        Files.createDirectories(dir);
        Files.write(dir.resolve("simple-fields.xlsx"), TemplateFixtures.simpleFields());
        Files.write(dir.resolve("table-each.xlsx"),    TemplateFixtures.tableEach());
        Files.write(dir.resolve("formulas.xlsx"),      TemplateFixtures.formulas());
    }
}
