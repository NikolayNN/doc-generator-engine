package io.github.nikolaynn.docengine.api;

import java.io.InputStream;
import java.util.Objects;

/**
 * Reference to a template. Deliberately open for extension: a custom
 * implementation (e.g. a key into S3/classpath/DB storage) is resolved to
 * bytes by the {@link io.github.nikolaynn.docengine.spi.TemplateResolver}
 * configured on the engine.
 */
public interface TemplateReference {

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
