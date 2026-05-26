package com.example.docengine.internal.resolver;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.TemplateReference;
import com.example.docengine.api.exception.TemplateResolutionException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputStreamTemplateResolverTest {

    @Test
    void bytesRefReturnedAsIs() {
        var r = new InputStreamTemplateResolver();
        byte[] payload = {1, 2, 3};
        var resolved = r.resolve(new TemplateReference.BytesRef(payload, DocumentFormat.XLSX, "h"));
        assertThat(resolved.bytes()).isEqualTo(payload);
        assertThat(resolved.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(resolved.hint()).isEqualTo("h");
    }

    @Test
    void inputStreamRefIsReadFully() {
        var r = new InputStreamTemplateResolver();
        byte[] payload = {9, 8, 7, 6};
        var ref = new TemplateReference.InputStreamRef(
            new ByteArrayInputStream(payload), DocumentFormat.XLSX, "h");
        var resolved = r.resolve(ref);
        assertThat(resolved.bytes()).isEqualTo(payload);
    }

    @Test
    void ioErrorOnReadMappedToTemplateResolutionException() {
        var r = new InputStreamTemplateResolver();
        InputStream broken = new InputStream() {
            public int read() throws IOException { throw new IOException("boom"); }
        };
        assertThatThrownBy(() -> r.resolve(
            new TemplateReference.InputStreamRef(broken, DocumentFormat.XLSX, "h")))
            .isInstanceOf(TemplateResolutionException.class)
            .hasMessageContaining("h");
    }
}
