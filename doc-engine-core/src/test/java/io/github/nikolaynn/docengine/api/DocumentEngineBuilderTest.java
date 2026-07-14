package io.github.nikolaynn.docengine.api;

import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.Test;

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
}
