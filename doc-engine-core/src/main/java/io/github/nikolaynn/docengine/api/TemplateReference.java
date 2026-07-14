package io.github.nikolaynn.docengine.api;

import java.io.InputStream;
import java.util.Objects;

public sealed interface TemplateReference
        permits TemplateReference.InputStreamRef, TemplateReference.BytesRef {

    DocumentFormat sourceFormat();

    String hint();

    record InputStreamRef(InputStream stream, DocumentFormat sourceFormat, String hint)
            implements TemplateReference {
        public InputStreamRef {
            Objects.requireNonNull(stream, "stream");
            Objects.requireNonNull(sourceFormat, "sourceFormat");
        }
    }

    record BytesRef(byte[] bytes, DocumentFormat sourceFormat, String hint)
            implements TemplateReference {
        public BytesRef {
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(sourceFormat, "sourceFormat");
        }
    }
}
