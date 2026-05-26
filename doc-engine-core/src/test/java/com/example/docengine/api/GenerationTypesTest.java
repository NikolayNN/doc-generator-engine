package com.example.docengine.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationTypesTest {

    @Test
    void optionsDefaultsAreAllNullOrEmpty() {
        var opts = GenerationOptions.defaults();
        assertThat(opts.fileNameHint()).isNull();
        assertThat(opts.timeout()).isNull();
        assertThat(opts.locale()).isNull();
        assertThat(opts.engineHints()).isEmpty();
    }

    @Test
    void optionsConstructorNormalizesNullHintsToEmptyMap() {
        var opts = new GenerationOptions(null, Duration.ofSeconds(5), null, null);
        assertThat(opts.engineHints()).isEmpty();
    }

    @Test
    void requestStoresAllFields() {
        var ref = new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h");
        var data = Map.<String, Object>of("k", "v");
        var opts = GenerationOptions.defaults();
        var req = new GenerationRequest(ref, data, DocumentFormat.PDF, opts);

        assertThat(req.template()).isSameAs(ref);
        assertThat(req.data()).isSameAs(data);
        assertThat(req.targetFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(req.options()).isSameAs(opts);
    }

    @Test
    void requestRejectsNullTemplateAndTargetFormat() {
        var data = Map.<String, Object>of();
        var opts = GenerationOptions.defaults();
        assertThatThrownBy(() -> new GenerationRequest(null, data, DocumentFormat.PDF, opts))
            .isInstanceOf(NullPointerException.class);
        var ref = new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h");
        assertThatThrownBy(() -> new GenerationRequest(ref, data, null, opts))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requestNormalizesNullDataToEmptyMapAndNullOptionsToDefaults() {
        var ref = new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h");
        var req = new GenerationRequest(ref, null, DocumentFormat.PDF, null);
        assertThat(req.data()).isEmpty();
        assertThat(req.options()).isEqualTo(GenerationOptions.defaults());
    }

    @Test
    void resultStoresAllFields() {
        byte[] payload = new byte[]{9, 9};
        var res = new GenerationResult("report.pdf", "application/pdf", DocumentFormat.PDF, payload);
        assertThat(res.fileName()).isEqualTo("report.pdf");
        assertThat(res.mimeType()).isEqualTo("application/pdf");
        assertThat(res.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(res.content()).isSameAs(payload);
    }
}
