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

    /** A reference to in-memory template bytes. */
    static TemplateReference ofBytes(byte[] bytes, DocumentFormat sourceFormat, String hint) {
        return new BytesRef(bytes, sourceFormat, hint);
    }

    /**
     * A reference to a template stream. The stream is consumed once during
     * resolution, so a reference is single-use.
     */
    static TemplateReference ofStream(InputStream stream, DocumentFormat sourceFormat, String hint) {
        return new InputStreamRef(stream, sourceFormat, hint);
    }

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
