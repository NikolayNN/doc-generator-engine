package io.github.nikolaynn.docengine.jod;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JodDocumentConverterTest {

    @Test
    void supportsXlsxToPdfOnly() {
        var c = new JodDocumentConverter(mock(OfficeManager.class), Duration.ofSeconds(5));
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).isTrue();
        assertThat(c.supports(DocumentFormat.PDF, DocumentFormat.XLSX)).isFalse();
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.XLSX)).isFalse();
    }

    @Test
    void doesNotStartPoolOnConstruction() {
        OfficeManager manager = mock(OfficeManager.class);
        new JodDocumentConverter(manager, Duration.ofSeconds(5));
        verifyNoInteractions(manager);
    }

    @Test
    void startsPoolOnceOnFirstConversion(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        // мок-менеджер не делает работы, срабатывает защита "пустой вывод" — здесь это ок
        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class);
        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class);

        verify(manager, times(1)).start();
    }

    @Test
    void closeStopsStartedPoolAndIsIdempotent() throws Exception {
        OfficeManager manager = runningManager();
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        c.start();

        c.close();
        c.close();

        verify(manager, times(1)).stop();
    }

    @Test
    void closeWithoutStartDoesNotStopManager() throws Exception {
        OfficeManager manager = mock(OfficeManager.class);
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));

        c.close();

        verify(manager, never()).stop();
    }

    @Test
    void buildFailureMapsToConversionException(@TempDir Path tmp) {
        // an explicit officeHome that isn't a LibreOffice install makes
        // LocalOfficeManager.builder().build() throw a raw IllegalStateException;
        // it must be wrapped, not leak past the public API
        Path bogusHome = tmp.resolve("no-such-office-home");
        var c = new JodDocumentConverter(
            JodDocumentConverter.Config.builder().officeHome(bogusHome).build());

        assertThatThrownBy(c::start)
            .isInstanceOf(DocumentConversionException.class);
    }

    @Test
    void stopsPoolWhenStartFails() throws Exception {
        // a partial start may have already spawned pool processes; they must be
        // released, otherwise close() (which skips stop() when started==false)
        // leaks them
        OfficeManager manager = mock(OfficeManager.class);
        doThrow(new OfficeException("start boom")).when(manager).start();
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));

        assertThatThrownBy(c::start)
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("start boom");

        verify(manager).stop();
    }

    @Test
    void convertAfterCloseThrowsIllegalState(@TempDir Path tmp) throws Exception {
        var c = new JodDocumentConverter(mock(OfficeManager.class), Duration.ofSeconds(5));
        c.close();
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void officeExceptionMapsToConversionException(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        doThrow(new OfficeException("boom")).when(manager).execute(any());
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("boom")
            .matches(e -> !((DocumentConversionException) e).isTimeout());
    }

    @Test
    void timeoutCauseMapsToTimeoutConversionException(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        doThrow(new OfficeException("task timed out", new TimeoutException("120s")))
            .when(manager).execute(any());
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class)
            .matches(e -> ((DocumentConversionException) e).isTimeout());
    }

    @Test
    void cleansUpManagedTempFileOnUncheckedException(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        doThrow(new RuntimeException("kaboom")).when(manager).execute(any());
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("kaboom");

        try (var files = Files.list(tmp)) {
            assertThat(files.filter(p -> p.getFileName().toString().startsWith("doc-engine-pdf-")))
                .isEmpty();
        }
    }

    @Test
    void rejectsUnsupportedPair(@TempDir Path tmp) throws Exception {
        var c = new JodDocumentConverter(mock(OfficeManager.class), Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.pdf"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.PDF, DocumentFormat.XLSX, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("unsupported");
    }

    /** LocalConverter отклоняет не-запущенный менеджер, поэтому стабим isRunning(). */
    private static OfficeManager runningManager() {
        OfficeManager manager = mock(OfficeManager.class);
        lenient().when(manager.isRunning()).thenReturn(true);
        return manager;
    }

    private static ConvertContext ctx(TempFileManager tfm) {
        return new ConvertContext(Duration.ofSeconds(5), tfm, "tpl");
    }
}
