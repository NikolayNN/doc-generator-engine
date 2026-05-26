package com.example.docengine.internal.validator;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.TemplateReference;
import org.junit.jupiter.api.Test;

class NoopTemplateValidatorTest {

    @Test
    void validateNeverThrowsForAnyReference() {
        var v = new NoopTemplateValidator();
        v.validate(new TemplateReference.BytesRef(new byte[0], DocumentFormat.XLSX, "h"));
    }

    @Test
    void validateNeverThrowsForNull() {
        new NoopTemplateValidator().validate(null);
    }
}
