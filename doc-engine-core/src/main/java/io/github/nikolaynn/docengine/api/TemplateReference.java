package io.github.nikolaynn.docengine.api;

import java.io.InputStream;
import java.util.Arrays;
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

        // The default record equals/hashCode compare the byte[] by identity, so two
        // references holding the same template bytes would not be equal. Override for
        // value semantics (content-based), which is what callers expect.
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytesRef other)) return false;
            return Arrays.equals(bytes, other.bytes)
                && sourceFormat == other.sourceFormat
                && Objects.equals(hint, other.hint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(bytes), sourceFormat, hint);
        }
    }
}
