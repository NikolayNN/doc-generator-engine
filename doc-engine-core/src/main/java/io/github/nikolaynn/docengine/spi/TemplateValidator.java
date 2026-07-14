package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.TemplateReference;

public interface TemplateValidator {
    void validate(TemplateReference ref);
}
