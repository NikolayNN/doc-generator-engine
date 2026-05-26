package com.example.docengine.spi;

import com.example.docengine.api.DocumentFormat;

import java.nio.file.Path;

public interface DocumentConverter {
    boolean supports(DocumentFormat from, DocumentFormat to);

    /** Converts the input file to target format; returns a new file. */
    Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx);
}
