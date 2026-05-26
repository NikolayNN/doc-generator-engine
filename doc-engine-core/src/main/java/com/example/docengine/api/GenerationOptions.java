package com.example.docengine.api;

import java.time.Duration;
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
        engineHints = engineHints == null ? Map.of() : Map.copyOf(engineHints);
    }

    public static GenerationOptions defaults() {
        return DEFAULTS;
    }
}
