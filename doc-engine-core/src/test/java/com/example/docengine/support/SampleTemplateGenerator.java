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
 * Run all on demand:
 * <pre>
 *   mvn -pl doc-engine-core test -Dtest=SampleTemplateGenerator -Dregenerate.samples=true
 * </pre>
 *
 * Or a single fixture (useful when others are open in Excel):
 * <pre>
 *   mvn -pl doc-engine-core test \
 *     -Dtest=SampleTemplateGenerator#regenerateTableEach \
 *     -Dregenerate.samples=true
 * </pre>
 */
class SampleTemplateGenerator {

    private static final Path DIR = Path.of("src/test/resources/templates");

    @Test
    @EnabledIfSystemProperty(named = "regenerate.samples", matches = "true")
    void regenerateSimpleFields() throws Exception {
        Files.createDirectories(DIR);
        Files.write(DIR.resolve("simple-fields.xlsx"), TemplateFixtures.simpleFields());
    }

    @Test
    @EnabledIfSystemProperty(named = "regenerate.samples", matches = "true")
    void regenerateTableEach() throws Exception {
        Files.createDirectories(DIR);
        Files.write(DIR.resolve("table-each.xlsx"), TemplateFixtures.tableEach());
    }

    @Test
    @EnabledIfSystemProperty(named = "regenerate.samples", matches = "true")
    void regenerateFormulas() throws Exception {
        Files.createDirectories(DIR);
        Files.write(DIR.resolve("formulas.xlsx"), TemplateFixtures.formulas());
    }
}
