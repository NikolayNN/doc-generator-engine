package io.github.nikolaynn.docengine.jod;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("sofficeAvailable")
class JodDocumentConverterIT {

    static boolean sofficeAvailable() {
        try {
            Process p = new ProcessBuilder("soffice", "--version")
                .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    void convertsXlsxToPdfOnPooledProcess(@TempDir Path tmp) throws Exception {
        try (JodDocumentConverter converter = new JodDocumentConverter(
                JodDocumentConverter.Config.builder().workingDir(tmp).build())) {
            TempFileManager tfm = new DefaultTempFileManager(tmp, false);
            Path xlsx = writeSampleWorkbook(tmp.resolve("in.xlsx"));

            Path pdf = converter.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(60), tfm, "it"));

            assertThat(pdf).exists();
            assertThat(Files.size(pdf)).isGreaterThan(100);
        }
    }

    @Test
    void handlesFourConcurrentConversions(@TempDir Path tmp) throws Exception {
        try (JodDocumentConverter converter = new JodDocumentConverter(
                JodDocumentConverter.Config.builder().poolSize(4).workingDir(tmp).build())) {
            TempFileManager tfm = new DefaultTempFileManager(tmp, false);
            List<Callable<Path>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Path xlsx = writeSampleWorkbook(tmp.resolve("in-" + i + ".xlsx"));
                jobs.add(() -> converter.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
                    new ConvertContext(Duration.ofSeconds(60), tfm, "it")));
            }

            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                for (Future<Path> f : pool.invokeAll(jobs)) {
                    assertThat(Files.size(f.get())).isGreaterThan(100);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static Path writeSampleWorkbook(Path target) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sh = wb.createSheet("data");
            sh.createRow(0).createCell(0).setCellValue("hello from jod pool");
            try (var out = Files.newOutputStream(target)) {
                wb.write(out);
            }
        }
        return target;
    }
}
