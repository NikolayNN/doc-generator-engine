package com.example.docengine.api.exception;

import com.example.docengine.api.DocumentFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentGenerationExceptionTest {

    @Test
    void rootExceptionExposesContextFields() {
        var ex = new TemplateRenderingException(
            "tpl.xlsx", DocumentFormat.XLSX, DocumentFormat.PDF, "boom", new RuntimeException("cause"));
        assertThat(ex.getTemplateHint()).isEqualTo("tpl.xlsx");
        assertThat(ex.getSourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ex.getTargetFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(ex.getMessage()).contains("boom").contains("tpl.xlsx");
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void documentConversionExceptionTracksTimeoutFlag() {
        var ex = DocumentConversionException.timeout(
            "tpl.xlsx", DocumentFormat.XLSX, DocumentFormat.PDF, java.time.Duration.ofSeconds(5));
        assertThat(ex.isTimeout()).isTrue();
        assertThat(ex.getMessage()).contains("timeout").contains("5");
    }

    @Test
    void unsupportedTemplateFormatExceptionUsesNullableTarget() {
        var ex = new UnsupportedTemplateFormatException("h", DocumentFormat.XLSX);
        assertThat(ex.getSourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ex.getTargetFormat()).isNull();
    }

    @Test
    void allSubtypesExtendRoot() {
        assertThat(new InvalidGenerationRequestException("m")).isInstanceOf(DocumentGenerationException.class);
        assertThat(new TempFileException("h", null, null, "m", null)).isInstanceOf(DocumentGenerationException.class);
        assertThat(new TemplateResolutionException("h", DocumentFormat.XLSX, "m", null))
            .isInstanceOf(DocumentGenerationException.class);
        assertThat(new TemplateValidationException("h", DocumentFormat.XLSX, "m"))
            .isInstanceOf(DocumentGenerationException.class);
        assertThat(new UnsupportedConversionException("h", DocumentFormat.XLSX, DocumentFormat.PDF))
            .isInstanceOf(DocumentGenerationException.class);
    }
}
