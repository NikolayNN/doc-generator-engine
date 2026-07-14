package io.github.nikolaynn.docengine.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single generation request.
 *
 * @param template the template to render (bytes, stream, or a custom reference)
 * @param data the data model exposed to the template; may contain null values,
 *        {@code null} is treated as empty
 * @param targetFormat the desired output format (a conversion runs when it differs
 *        from the template's source format)
 * @param options generation options; {@code null} means {@link GenerationOptions#defaults()}
 */
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
