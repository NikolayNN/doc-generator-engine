package com.example.docengine.internal.libreoffice;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.spi.ConvertContext;
import com.example.docengine.spi.TempFileManager;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("sofficeAvailable")
class LibreOfficeConverterIT {

    static boolean sofficeAvailable() {
        try {
            Process p = new ProcessBuilder("soffice", "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    void convertsXlsxToPdf(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("in.xlsx");
        Files.write(xlsx, com.example.docengine.support.TemplateFixtures.simpleFields());
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        var c = new LibreOfficeConverter(null, Duration.ofSeconds(60), tmp);

        Path pdf = c.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
            new ConvertContext(Duration.ofSeconds(60), tfm, "it"));

        assertThat(pdf).exists();
        assertThat(Files.size(pdf)).isGreaterThan(100);
    }
}
