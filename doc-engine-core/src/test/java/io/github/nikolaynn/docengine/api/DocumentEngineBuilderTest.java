package io.github.nikolaynn.docengine.api;

import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import io.github.nikolaynn.docengine.support.TemplateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DocumentEngineBuilderTest {

    @Test
    void builderRequiresAtLeastOneTemplateEngineAndTempFileManager() {
        assertThatThrownBy(() -> DocumentEngineBuilder.create().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("templateEngine");
    }

    @Test
    void buildsEngineWhenAllRequiredDepsProvided() {
        TempFileManager tfm = mock(TempFileManager.class);
        TemplateResolver tr = mock(TemplateResolver.class);
        TemplateValidator tv = mock(TemplateValidator.class);
        TemplateEngine te = mock(TemplateEngine.class);
        DocumentConverter dc = mock(DocumentConverter.class);

        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(tfm)
            .templateResolver(tr)
            .templateValidator(tv)
            .addTemplateEngine(te)
            .addConverter(dc)
            .build();

        assertThat(engine).isNotNull();
    }

    @Test
    void withDefaultsBuildsEngineThatRendersXlsx() throws Exception {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .withDefaults()
            .build();

        GenerationResult result = engine.generate(new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "quickstart"),
            Map.of("greeting", "Hello", "name", "World"),
            DocumentFormat.XLSX,
            null));

        assertThat(result.content()).isNotEmpty();
        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
    }

    @Test
    void factoryMethodsWireDefaultImplementationsWithoutInternalImports(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .withJxlsEngine()
            .withLibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(5), tmp)
            .withDefaultTempFileManager(tmp, false)
            .build();

        GenerationResult result = engine.generate(new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "wired"),
            Map.of("greeting", "Hi", "name", "There"),
            DocumentFormat.XLSX,
            null));

        assertThat(result.content()).isNotEmpty();
    }
}
