package io.github.nikolaynn.docengine.starter;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMetadataTest {

    @Test
    void generatedMetadataListsPropertiesWithMergedDefaults() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring-configuration-metadata.json")) {
            assertThat(in).as("config-processor generated metadata on classpath").isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // properties emitted from DocEngineProperties by the processor
        assertThat(json)
            .contains("doc-engine.temp-dir")
            .contains("doc-engine.cleanup-on-shutdown")
            .contains("doc-engine.converter.libreoffice.timeout")
            .contains("doc-engine.converter.jod.pool-size")
            .contains("doc-engine.converter.jod.max-tasks-per-process");
        // a default merged from additional-spring-configuration-metadata.json:
        // "120s" is the jod.task-timeout default and cannot be inferred by the
        // processor, so it is present ONLY after the additional file is added
        assertThat(json).contains("120s");
    }
}
