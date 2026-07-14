package io.github.nikolaynn.docengine.internal.validator;

import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.spi.TemplateValidator;

public class NoopTemplateValidator implements TemplateValidator {
    @Override
    public void validate(TemplateReference ref) {
        // intentionally empty: real validation arrives in a later version
    }
}
