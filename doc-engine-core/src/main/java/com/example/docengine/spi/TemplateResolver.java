package com.example.docengine.spi;

import com.example.docengine.api.TemplateReference;

public interface TemplateResolver {
    ResolvedTemplate resolve(TemplateReference ref);
}
