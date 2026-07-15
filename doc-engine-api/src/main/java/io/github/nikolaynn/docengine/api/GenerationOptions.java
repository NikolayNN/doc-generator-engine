package io.github.nikolaynn.docengine.api;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Options for a single generation. All fields are optional; use {@link #builder()}
 * for readable construction or {@link #defaults()} for none.
 *
 * <p>Some options are advisory — the bundled components do not all honor them.
 * {@code locale} and {@code engineHints} are passed only to
 * {@link io.github.nikolaynn.docengine.spi.RenderContext}, so a custom
 * {@link io.github.nikolaynn.docengine.spi.TemplateEngine} may honor them; a
 * {@link io.github.nikolaynn.docengine.spi.DocumentConverter} receives only
 * {@code timeout} (via {@link io.github.nikolaynn.docengine.spi.ConvertContext}).
 * Of these advisory options, the only one any bundled component honors is
 * {@code timeout}, and only the process-based LibreOffice converter honors it
 * (see the per-field notes below).
 *
 * @param fileNameHint base name for the produced file (the extension is appended
 *        when missing); when {@code null} or blank a name is derived from the
 *        template hint
 * @param timeout conversion timeout; honored ONLY by the process-based LibreOffice
 *        converter. The JXLS renderer and the JODConverter pool ignore it (the
 *        pool applies its own configured task timeout)
 * @param locale advisory: the bundled JXLS engine does not apply it; a custom
 *        {@code TemplateEngine} receives it via
 *        {@link io.github.nikolaynn.docengine.spi.RenderContext} and may honor it
 * @param engineHints advisory generic pass-through: the bundled JXLS engine ignores
 *        these; a custom TemplateEngine receives them via RenderContext. A
 *        DocumentConverter never receives them (ConvertContext has no such field)
 */
public record GenerationOptions(
        String fileNameHint,
        Duration timeout,
        Locale locale,
        Map<String, Object> engineHints
) {
    private static final GenerationOptions DEFAULTS =
            new GenerationOptions(null, null, null, Map.of());

    public GenerationOptions {
        // null-tolerant copy: Map.copyOf rejects null values
        engineHints = engineHints == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(engineHints));
    }

    /** Options with nothing set. */
    public static GenerationOptions defaults() {
        return DEFAULTS;
    }

    /** A fresh builder; all fields start unset. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder pre-populated with this instance's values, for producing a
     * modified copy by tweaking individual fields.
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.fileNameHint = fileNameHint;
        b.timeout = timeout;
        b.locale = locale;
        b.engineHints = new LinkedHashMap<>(engineHints);
        return b;
    }

    /** Mutable builder for {@link GenerationOptions}. */
    public static final class Builder {
        private String fileNameHint;
        private Duration timeout;
        private Locale locale;
        private Map<String, Object> engineHints;

        private Builder() {}

        public Builder fileNameHint(String v) { this.fileNameHint = v; return this; }

        public Builder timeout(Duration v) { this.timeout = v; return this; }

        public Builder locale(Locale v) { this.locale = v; return this; }

        /** Replaces the accumulated hints ({@code null} clears them). */
        public Builder engineHints(Map<String, Object> hints) {
            this.engineHints = hints == null ? null : new LinkedHashMap<>(hints);
            return this;
        }

        /** Adds or overwrites a single hint, preserving insertion order. */
        public Builder engineHint(String key, Object value) {
            if (engineHints == null) {
                engineHints = new LinkedHashMap<>();
            }
            engineHints.put(key, value);
            return this;
        }

        public GenerationOptions build() {
            return new GenerationOptions(fileNameHint, timeout, locale, engineHints);
        }
    }
}
