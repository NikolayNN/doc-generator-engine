package com.example.docengine.api;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
}
