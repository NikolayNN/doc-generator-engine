package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
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
    void renderContextToleratesNullEngineHintValues() {
        // GenerationOptions deliberately allows null hint values, so the context
        // built from them must not reject what the options accepted
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("k", null);

        var ctx = new RenderContext(null, null, hints, noopTfm(), "hint");

        assertThat(ctx.engineHints()).containsEntry("k", null);
    }

    @Test
    void renderContextPreservesEngineHintInsertionOrder() {
        Map<String, Object> hints = new LinkedHashMap<>();
        for (String key : new String[]{"z", "q", "a", "m", "x", "b", "f", "c", "y", "d"}) {
            hints.put(key, key);
        }

        var ctx = new RenderContext(null, null, hints, noopTfm(), "hint");

        assertThat(ctx.engineHints().keySet())
            .containsExactly("z", "q", "a", "m", "x", "b", "f", "c", "y", "d");
    }

    private static TempFileManager noopTfm() {
        return new TempFileManager() {
            public Path createTempFile(String prefix, String suffix) { return null; }
            public void delete(Path path) {}
        };
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
