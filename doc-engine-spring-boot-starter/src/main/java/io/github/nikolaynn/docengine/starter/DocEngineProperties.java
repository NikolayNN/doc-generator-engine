package io.github.nikolaynn.docengine.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration for the doc-engine Spring Boot starter, bound from {@code doc-engine.*}.
 *
 * @param tempDir directory for temporary files; {@code null} uses the system temp dir
 * @param cleanupOnShutdown delete tracked temp files on JVM shutdown (default {@code true})
 * @param converter converter configuration
 */
@ConfigurationProperties("doc-engine")
public record DocEngineProperties(
        Path tempDir,
        @DefaultValue("true") boolean cleanupOnShutdown,
        Converter converter
) {
    public DocEngineProperties {
        if (converter == null) {
            converter = new Converter(null, null);
        }
    }

    /**
     * Converter configuration.
     *
     * @param libreoffice process-based LibreOffice converter settings
     * @param jod pooled JODConverter settings (used when the doc-engine-jodconverter module is present)
     */
    public record Converter(LibreOffice libreoffice, Jod jod) {
        public Converter {
            if (libreoffice == null) {
                libreoffice = new LibreOffice(true, null, null, null);
            }
            if (jod == null) {
                jod = new Jod(true, null, 1, 2002, null, null, 200, null);
            }
        }
    }

    /**
     * Process-based LibreOffice converter.
     *
     * @param enabled whether the converter is created (disable if PDF is never needed)
     * @param executable path to the soffice executable; {@code null} looks it up on PATH
     * @param timeout per-conversion timeout
     * @param workingDir working directory for the soffice process; {@code null} uses the system temp dir
     */
    public record LibreOffice(boolean enabled, Path executable, Duration timeout, Path workingDir) {
        public LibreOffice {
            if (timeout == null) timeout = Duration.ofSeconds(60);
        }
    }

    /**
     * Pooled JODConverter (long-lived LibreOffice processes).
     *
     * @param enabled whether the pooled converter is created when the module is on the classpath
     * @param officeHome LibreOffice installation directory; {@code null} auto-detects
     * @param poolSize number of LibreOffice processes in the pool
     * @param basePort first local TCP port of the pool (process N listens on basePort+N);
     *        co-located applications must be given disjoint ranges
     * @param taskTimeout per-task execution timeout
     * @param taskQueueTimeout how long a task waits for a free process
     * @param maxTasksPerProcess restart a process after this many tasks
     * @param existingProcessAction what to do when a process already listens on a pool
     *        port: FAIL, KILL, CONNECT or CONNECT_OR_KILL; {@code null} keeps the
     *        JODConverter default (KILL)
     */
    public record Jod(boolean enabled,
                      Path officeHome,
                      int poolSize,
                      int basePort,
                      Duration taskTimeout,
                      Duration taskQueueTimeout,
                      int maxTasksPerProcess,
                      String existingProcessAction) {
        public Jod {
            if (poolSize < 1) poolSize = 1;
            if (basePort < 1) basePort = 2002;
            if (taskTimeout == null) taskTimeout = Duration.ofSeconds(120);
            if (taskQueueTimeout == null) taskQueueTimeout = Duration.ofSeconds(30);
            if (maxTasksPerProcess < 1) maxTasksPerProcess = 200;
        }
    }
}
