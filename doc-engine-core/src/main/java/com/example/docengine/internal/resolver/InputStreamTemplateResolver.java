package com.example.docengine.internal.resolver;

import com.example.docengine.api.TemplateReference;
import com.example.docengine.api.exception.TemplateResolutionException;
import com.example.docengine.spi.ResolvedTemplate;
import com.example.docengine.spi.TemplateResolver;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamTemplateResolver implements TemplateResolver {

    @Override
    public ResolvedTemplate resolve(TemplateReference ref) {
        return switch (ref) {
            case TemplateReference.BytesRef b ->
                new ResolvedTemplate(b.bytes(), b.sourceFormat(), b.hint());
            case TemplateReference.InputStreamRef s ->
                new ResolvedTemplate(readAll(s.stream(), s.hint(), s.sourceFormat()),
                                     s.sourceFormat(), s.hint());
        };
    }

    private static byte[] readAll(InputStream in,
                                  String hint,
                                  com.example.docengine.api.DocumentFormat sourceFormat) {
        try (InputStream is = in) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new TemplateResolutionException(hint, sourceFormat,
                "failed to read template stream", e);
        }
    }
}
