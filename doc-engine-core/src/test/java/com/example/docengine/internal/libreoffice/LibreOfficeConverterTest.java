package com.example.docengine.internal.libreoffice;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.exception.DocumentConversionException;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.spi.ConvertContext;
import com.example.docengine.spi.TempFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibreOfficeConverterTest {

    @Test
    void supportsXlsxToPdfOnly() {
        var c = new LibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(60), null);
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).isTrue();
        assertThat(c.supports(DocumentFormat.PDF, DocumentFormat.XLSX)).isFalse();
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.XLSX)).isFalse();
    }

    @Test
    void missingExecutableMapsToConversionException(@TempDir Path tmp) throws Exception {
        Path input = Files.createTempFile(tmp, "in", ".xlsx");
        Files.writeString(input, "stub");
        Path bogusExe = tmp.resolve("definitely-not-soffice");
        var c = new LibreOfficeConverter(bogusExe, Duration.ofSeconds(5), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(5), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class);
    }

    @Test
    void rejectsUnsupportedConversionPair(@TempDir Path tmp) throws Exception {
        Path input = Files.createTempFile(tmp, "in", ".pdf");
        Files.writeString(input, "stub");
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        var c = new LibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(5), tmp);

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.PDF, DocumentFormat.XLSX,
                new ConvertContext(Duration.ofSeconds(5), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("unsupported");
    }
}
