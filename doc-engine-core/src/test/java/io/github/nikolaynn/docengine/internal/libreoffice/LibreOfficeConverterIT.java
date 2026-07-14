package io.github.nikolaynn.docengine.internal.libreoffice;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
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
        Files.write(xlsx, io.github.nikolaynn.docengine.support.TemplateFixtures.simpleFields());
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        var c = new LibreOfficeConverter(null, Duration.ofSeconds(60), tmp);

        Path pdf = c.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
            new ConvertContext(Duration.ofSeconds(60), tfm, "it"));

        assertThat(pdf).exists();
        assertThat(Files.size(pdf)).isGreaterThan(100);
    }
}
