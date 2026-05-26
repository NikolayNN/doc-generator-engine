package com.example.docengine.internal.validator;

import com.example.docengine.api.TemplateReference;
import com.example.docengine.spi.TemplateValidator;

public class NoopTemplateValidator implements TemplateValidator {
    @Override
    public void validate(TemplateReference ref) {
        // intentionally empty: real validation arrives in a later version
    }
}
