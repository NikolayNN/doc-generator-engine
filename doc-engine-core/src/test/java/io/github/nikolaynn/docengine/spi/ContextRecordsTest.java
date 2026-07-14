package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextRecordsTest {

    @Test
    void renderContextStoresFields() {
        TempFileManager tfm = new TempFileManager() {
            public Path createTempFile(String prefix, String suffix) { return null; }
            public void delete(Path path) {}
        };
        var ctx = new RenderContext(Locale.ENGLISH, Duration.ofSeconds(10), Map.of("a", 1), tfm, "hint");
        assertThat(ctx.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(ctx.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(ctx.engineHints()).containsEntry("a", 1);
        assertThat(ctx.tempFileManager()).isSameAs(tfm);
        assertThat(ctx.templateHint()).isEqualTo("hint");
    }

    @Test
    void convertContextStoresFields() {
        TempFileManager tfm = new TempFileManager() {
            public Path createTempFile(String prefix, String suffix) { return null; }
            public void delete(Path path) {}
        };
        var ctx = new ConvertContext(Duration.ofSeconds(5), tfm, "h");
        assertThat(ctx.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(ctx.tempFileManager()).isSameAs(tfm);
        assertThat(ctx.templateHint()).isEqualTo("h");
    }

    @Test
    void resolvedTemplateExposesBytesAndFormat() {
        var rt = new ResolvedTemplate(new byte[]{1, 2}, DocumentFormat.XLSX, "h");
        assertThat(rt.bytes()).hasSize(2);
        assertThat(rt.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(rt.hint()).isEqualTo("h");
    }
}
