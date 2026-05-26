package com.example.docengine.spi;

import java.time.Duration;
import java.util.Objects;

public record ConvertContext(Duration timeout, TempFileManager tempFileManager, String templateHint) {
    public ConvertContext {
        Objects.requireNonNull(tempFileManager, "tempFileManager");
    }
}
