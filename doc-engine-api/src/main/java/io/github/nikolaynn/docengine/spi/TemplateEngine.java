package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;

import java.nio.file.Path;
import java.util.Map;

public interface TemplateEngine {
    boolean supports(DocumentFormat sourceFormat);

    /** Renders the template with data into a file of the same format as source. */
    Path render(ResolvedTemplate template, Map<String, Object> data, RenderContext ctx);
}
