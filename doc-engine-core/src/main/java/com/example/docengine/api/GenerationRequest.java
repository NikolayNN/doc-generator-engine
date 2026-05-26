package com.example.docengine.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record GenerationRequest(
        TemplateReference template,
        Map<String, Object> data,
        DocumentFormat targetFormat,
        GenerationOptions options
) {
    public GenerationRequest {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(targetFormat, "targetFormat");
        data = data == null ? Map.of() : Collections.unmodifiableMap(data);
        options = options == null ? GenerationOptions.defaults() : options;
    }
}
