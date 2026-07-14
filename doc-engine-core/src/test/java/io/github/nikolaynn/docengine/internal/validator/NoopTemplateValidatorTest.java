package io.github.nikolaynn.docengine.internal.validator;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.TemplateReference;
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
