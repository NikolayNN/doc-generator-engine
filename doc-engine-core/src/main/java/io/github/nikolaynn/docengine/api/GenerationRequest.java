package io.github.nikolaynn.docengine.api;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        // null-tolerant copy: template data legitimately contains null values,
        // which Map.copyOf would reject
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        options = options == null ? GenerationOptions.defaults() : options;
    }
}
