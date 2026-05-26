package com.example.docengine.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentFormatTest {

    @Test
    void xlsxHasOoxmlMimeAndXlsxExtension() {
        assertThat(DocumentFormat.XLSX.mimeType())
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(DocumentFormat.XLSX.extension()).isEqualTo("xlsx");
    }

    @Test
    void pdfHasApplicationPdfMimeAndPdfExtension() {
        assertThat(DocumentFormat.PDF.mimeType()).isEqualTo("application/pdf");
        assertThat(DocumentFormat.PDF.extension()).isEqualTo("pdf");
    }
}
