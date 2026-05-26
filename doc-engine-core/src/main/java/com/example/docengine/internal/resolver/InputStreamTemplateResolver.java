package com.example.docengine.internal.resolver;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.TemplateReference;
import com.example.docengine.api.exception.TemplateResolutionException;
import com.example.docengine.spi.ResolvedTemplate;
import com.example.docengine.spi.TemplateResolver;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamTemplateResolver implements TemplateResolver {

    @Override
    public ResolvedTemplate resolve(TemplateReference ref) {
        if (ref instanceof TemplateReference.BytesRef b) {
            return new ResolvedTemplate(b.bytes(), b.sourceFormat(), b.hint());
        }
        if (ref instanceof TemplateReference.InputStreamRef s) {
            return new ResolvedTemplate(
                readAll(s.stream(), s.hint(), s.sourceFormat()),
                s.sourceFormat(),
                s.hint());
        }
        throw new IllegalStateException(
            "TemplateReference subtype not handled: " + (ref == null ? "null" : ref.getClass()));
    }

    private static byte[] readAll(InputStream in, String hint, DocumentFormat sourceFormat) {
        try (InputStream is = in) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new TemplateResolutionException(hint, sourceFormat,
                "failed to read template stream", e);
        }
    }
}
