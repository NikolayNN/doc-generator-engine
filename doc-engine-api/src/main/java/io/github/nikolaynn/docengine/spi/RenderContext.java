package io.github.nikolaynn.docengine.spi;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record RenderContext(
        Locale locale,
        Duration timeout,
        Map<String, Object> engineHints,
        TempFileManager tempFileManager,
        String templateHint
) {
    public RenderContext {
        Objects.requireNonNull(tempFileManager, "tempFileManager");
        // null-tolerant, order-preserving copy: GenerationOptions deliberately
        // allows null hint values and insertion order, both of which Map.copyOf
        // would destroy
        engineHints = engineHints == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(engineHints));
    }
}
