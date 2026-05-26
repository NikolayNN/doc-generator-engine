package com.example.docengine.api;

import com.example.docengine.spi.DocumentConverter;
import com.example.docengine.spi.TempFileManager;
import com.example.docengine.spi.TemplateEngine;
import com.example.docengine.spi.TemplateResolver;
import com.example.docengine.spi.TemplateValidator;
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
