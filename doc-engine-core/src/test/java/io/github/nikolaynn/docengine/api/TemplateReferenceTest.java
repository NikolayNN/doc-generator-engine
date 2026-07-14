package io.github.nikolaynn.docengine.api;

import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.support.TemplateFixtures;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateReferenceTest {

    @Test
    void bytesRefStoresAllFields() {
        byte[] payload = new byte[]{1, 2, 3};
        var ref = new TemplateReference.BytesRef(payload, DocumentFormat.XLSX, "report.xlsx");
        assertThat(ref.bytes()).isSameAs(payload);
        assertThat(ref.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ref.hint()).isEqualTo("report.xlsx");
    }

    @Test
    void inputStreamRefStoresAllFields() {
        InputStream in = new ByteArrayInputStream(new byte[]{1});
        var ref = new TemplateReference.InputStreamRef(in, DocumentFormat.XLSX, "tpl");
        assertThat(ref.stream()).isSameAs(in);
        assertThat(ref.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ref.hint()).isEqualTo("tpl");
    }

    @Test
    void bytesRefRejectsNullBytes() {
        assertThatThrownBy(() -> new TemplateReference.BytesRef(null, DocumentFormat.XLSX, "x"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void inputStreamRefRejectsNullStream() {
        assertThatThrownBy(() -> new TemplateReference.InputStreamRef(null, DocumentFormat.XLSX, "x"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSourceFormat() {
        assertThatThrownBy(() -> new TemplateReference.BytesRef(new byte[0], null, "x"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void bytesRefEqualityIsValueBasedOnContent() {
        var a = new TemplateReference.BytesRef(new byte[]{1, 2, 3}, DocumentFormat.XLSX, "r");
        var b = new TemplateReference.BytesRef(new byte[]{1, 2, 3}, DocumentFormat.XLSX, "r");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void bytesRefDiffersOnContentFormatOrHint() {
        var base = new TemplateReference.BytesRef(new byte[]{1, 2, 3}, DocumentFormat.XLSX, "r");
        assertThat(base).isNotEqualTo(
            new TemplateReference.BytesRef(new byte[]{1, 2, 4}, DocumentFormat.XLSX, "r"));
        assertThat(base).isNotEqualTo(
            new TemplateReference.BytesRef(new byte[]{1, 2, 3}, DocumentFormat.PDF, "r"));
        assertThat(base).isNotEqualTo(
            new TemplateReference.BytesRef(new byte[]{1, 2, 3}, DocumentFormat.XLSX, "other"));
    }

    @Test
    void customReferenceTypeResolvesThroughCustomResolver() throws Exception {
        // a third-party reference type, e.g. a key into S3/classpath/DB storage
        record KeyRef(String key, DocumentFormat sourceFormat, String hint) implements TemplateReference {}

        byte[] templateBytes = TemplateFixtures.simpleFields();
        TemplateResolver byKey = ref -> {
            if (ref instanceof KeyRef k && "invoice".equals(k.key())) {
                return new ResolvedTemplate(templateBytes, k.sourceFormat(), k.hint());
            }
            throw new IllegalArgumentException("unknown reference: " + ref);
        };

        DocumentEngine engine = DocumentEngineBuilder.create()
            .withJxlsEngine()
            .withDefaultTempFileManager(null, false)
            .templateResolver(byKey)
            .build();

        GenerationResult result = engine.generate(new GenerationRequest(
            new KeyRef("invoice", DocumentFormat.XLSX, "invoice.xlsx"),
            Map.of("greeting", "Hello", "name", "Resolver"),
            DocumentFormat.XLSX,
            null));

        assertThat(result.content()).isNotEmpty();
    }
}
