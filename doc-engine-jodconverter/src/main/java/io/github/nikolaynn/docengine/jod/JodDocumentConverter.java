package io.github.nikolaynn.docengine.jod;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * XLSX->PDF converter backed by a pool of long-lived LibreOffice processes
 * managed by JODConverter: tens/hundreds of milliseconds per conversion on a
 * warm process instead of the multi-second soffice cold start.
 *
 * <p>The pool (and the underlying office manager) is created and started
 * lazily on the first conversion; call {@link #start()} to warm up eagerly.
 * {@link #close()} stops the pool and is idempotent.
 *
 * <p>{@code ConvertContext.timeout()} is NOT applied by this converter:
 * JODConverter configures the task timeout per manager, see
 * {@link Config#taskTimeout()}.
 */
public final class JodDocumentConverter implements DocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(JodDocumentConverter.class);
    private static final int BASE_PORT = 2002;

    private final Config config;          // null когда менеджер внедрён напрямую (тесты)
    private final Duration taskTimeout;   // для сообщения об ошибке таймаута
    private OfficeManager officeManager;  // создаётся лениво в start()
    private boolean started;
    private boolean closed;

    public JodDocumentConverter(Config config) {
        this.config = config == null ? Config.defaults() : config;
        this.taskTimeout = this.config.taskTimeout();
        this.officeManager = null;
    }

    JodDocumentConverter(OfficeManager officeManager, Duration taskTimeout) {
        this.config = null;
        this.taskTimeout = taskTimeout;
        this.officeManager = officeManager;
    }

    /** Pool configuration; all fields optional, see builder defaults. */
    public record Config(Path officeHome,
                         int poolSize,
                         Duration taskTimeout,
                         Duration taskQueueTimeout,
                         int maxTasksPerProcess,
                         Path workingDir) {
        public Config {
            if (poolSize < 1) throw new IllegalArgumentException("poolSize must be >= 1");
            if (maxTasksPerProcess < 1) throw new IllegalArgumentException("maxTasksPerProcess must be >= 1");
            taskTimeout = taskTimeout == null ? Duration.ofSeconds(120) : taskTimeout;
            taskQueueTimeout = taskQueueTimeout == null ? Duration.ofSeconds(30) : taskQueueTimeout;
        }

        public static Builder builder() { return new Builder(); }

        public static Config defaults() { return builder().build(); }

        public static final class Builder {
            private Path officeHome;
            private int poolSize = 1;
            private Duration taskTimeout = Duration.ofSeconds(120);
            private Duration taskQueueTimeout = Duration.ofSeconds(30);
            private int maxTasksPerProcess = 200;
            private Path workingDir;

            public Builder officeHome(Path v) { this.officeHome = v; return this; }
            public Builder poolSize(int v) { this.poolSize = v; return this; }
            public Builder taskTimeout(Duration v) { this.taskTimeout = v; return this; }
            public Builder taskQueueTimeout(Duration v) { this.taskQueueTimeout = v; return this; }
            public Builder maxTasksPerProcess(int v) { this.maxTasksPerProcess = v; return this; }
            public Builder workingDir(Path v) { this.workingDir = v; return this; }

            public Config build() {
                return new Config(officeHome, poolSize, taskTimeout, taskQueueTimeout,
                    maxTasksPerProcess, workingDir);
            }
        }
    }

    /**
     * Starts the LibreOffice pool eagerly (otherwise it starts on the first
     * conversion). Idempotent while the converter is open.
     */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("converter is closed");
        }
        if (started) {
            return;
        }
        if (officeManager == null) {
            // построение отложено сюда: LocalOfficeManager.builder() валидирует
            // officeHome и падает на машинах без установленного LibreOffice
            officeManager = buildManager(config);
        }
        try {
            officeManager.start();
            started = true;
        } catch (OfficeException e) {
            throw new DocumentConversionException(null, DocumentFormat.XLSX, DocumentFormat.PDF,
                "failed to start LibreOffice pool: " + e.getMessage(), e, false);
        }
    }

    @Override
    public boolean supports(DocumentFormat from, DocumentFormat to) {
        return from == DocumentFormat.XLSX && to == DocumentFormat.PDF;
    }

    @Override
    public Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx) {
        if (!supports(from, to)) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "unsupported conversion " + from + "->" + to, null, false);
        }
        start();

        Path managed = ctx.tempFileManager().createTempFile("doc-engine-pdf-", "." + to.extension());
        try {
            // LocalConverter.convert(File) не требует, чтобы officeManager реализовывал
            // TemporaryFileMaker (это нужно только для convert(InputStream)); мок
            // плоского OfficeManager (start/stop/isRunning/execute) этой цепочке достаточен.
            LocalConverter.builder().officeManager(officeManager).build()
                .convert(input.toFile())
                .to(managed.toFile())
                .execute();
            if (Files.size(managed) == 0) {
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "LibreOffice produced no output", null, false);
            }
            log.debug("converted {} -> {}", input, managed);
            return managed;
        } catch (OfficeException e) {
            ctx.tempFileManager().delete(managed);
            if (hasTimeoutCause(e)) {
                throw DocumentConversionException.timeout(ctx.templateHint(), from, to, taskTimeout);
            }
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "LibreOffice conversion failed: " + e.getMessage(), e, false);
        } catch (IOException e) {
            ctx.tempFileManager().delete(managed);
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "failed to read conversion output", e, false);
        } catch (DocumentConversionException e) {
            ctx.tempFileManager().delete(managed);
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (started) {
            try {
                officeManager.stop();
            } catch (OfficeException e) {
                log.warn("failed to stop LibreOffice pool: {}", e.getMessage());
            }
        }
    }

    private static OfficeManager buildManager(Config config) {
        LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
            .portNumbers(IntStream.range(BASE_PORT, BASE_PORT + config.poolSize()).toArray())
            .taskExecutionTimeout(config.taskTimeout().toMillis())
            .taskQueueTimeout(config.taskQueueTimeout().toMillis())
            .maxTasksPerProcess(config.maxTasksPerProcess());
        if (config.officeHome() != null) {
            builder.officeHome(config.officeHome().toFile());
        }
        if (config.workingDir() != null) {
            builder.workingDir(config.workingDir().toFile());
        }
        return builder.build();
    }

    private static boolean hasTimeoutCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
