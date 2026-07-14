package io.github.nikolaynn.docengine.api;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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

    public static GenerationOptions defaults() {
        return DEFAULTS;
    }
}
