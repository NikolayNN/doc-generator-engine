package io.github.nikolaynn.docengine.spi;

import java.time.Duration;
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
        engineHints = engineHints == null ? Map.of() : Map.copyOf(engineHints);
    }
}
