package io.github.nikolaynn.docengine.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class GenerationOptionsTest {

    @Test
    void builderMatchesCanonicalConstructor() {
        var built = GenerationOptions.builder()
            .fileNameHint("report")
            .timeout(Duration.ofSeconds(30))
            .locale(Locale.GERMANY)
            .engineHints(Map.of("k", "v"))
            .build();
        var canonical = new GenerationOptions("report", Duration.ofSeconds(30),
            Locale.GERMANY, Map.of("k", "v"));
        assertThat(built).isEqualTo(canonical);
    }

    @Test
    void engineHintAccumulatesInInsertionOrder() {
        var opts = GenerationOptions.builder()
            .engineHint("a", 1)
            .engineHint("b", 2)
            .build();
        assertThat(opts.engineHints()).containsExactly(entry("a", 1), entry("b", 2));
    }

    @Test
    void engineHintsReplacesAccumulatedHints() {
        var opts = GenerationOptions.builder()
            .engineHint("a", 1)
            .engineHints(Map.of("b", 2))
            .build();
        assertThat(opts.engineHints()).containsOnlyKeys("b");
    }

    @Test
    void emptyBuilderEqualsDefaults() {
        assertThat(GenerationOptions.builder().build())
            .isEqualTo(GenerationOptions.defaults());
    }

    @Test
    void nullSettersAreTolerated() {
        var opts = GenerationOptions.builder()
            .fileNameHint(null).timeout(null).locale(null).engineHints(null)
            .build();
        assertThat(opts.fileNameHint()).isNull();
        assertThat(opts.timeout()).isNull();
        assertThat(opts.locale()).isNull();
        assertThat(opts.engineHints()).isEmpty();
    }

    @Test
    void toBuilderRoundTripsAllFields() {
        var original = new GenerationOptions("report", Duration.ofSeconds(30),
            Locale.GERMANY, Map.of("k", "v"));
        assertThat(original.toBuilder().build()).isEqualTo(original);
    }

    @Test
    void toBuilderAllowsSelectiveOverride() {
        var original = new GenerationOptions("report", Duration.ofSeconds(30),
            Locale.GERMANY, Map.of("k", "v"));
        var modified = original.toBuilder().timeout(Duration.ofSeconds(5)).build();
        assertThat(modified.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(modified.fileNameHint()).isEqualTo("report");
        assertThat(modified.locale()).isEqualTo(Locale.GERMANY);
        assertThat(modified.engineHints()).containsExactly(entry("k", "v"));
    }
}
