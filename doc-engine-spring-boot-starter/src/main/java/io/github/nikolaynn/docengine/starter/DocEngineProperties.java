package io.github.nikolaynn.docengine.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("doc-engine")
public record DocEngineProperties(
        Path tempDir,
        boolean cleanupOnShutdown,
        Converter converter
) {
    public DocEngineProperties {
        if (converter == null) {
            converter = new Converter(null, null);
        }
    }

    public record Converter(LibreOffice libreoffice, Jod jod) {
        public Converter {
            if (libreoffice == null) {
                libreoffice = new LibreOffice(true, null, null, null);
            }
            if (jod == null) {
                jod = new Jod(true, null, 1, null, null, 200);
            }
        }
    }

    public record LibreOffice(boolean enabled, Path executable, Duration timeout, Path workingDir) {
        public LibreOffice {
            if (timeout == null) timeout = Duration.ofSeconds(60);
        }
    }

    public record Jod(boolean enabled,
                      Path officeHome,
                      int poolSize,
                      Duration taskTimeout,
                      Duration taskQueueTimeout,
                      int maxTasksPerProcess) {
        public Jod {
            if (poolSize < 1) poolSize = 1;
            if (taskTimeout == null) taskTimeout = Duration.ofSeconds(120);
            if (taskQueueTimeout == null) taskQueueTimeout = Duration.ofSeconds(30);
            if (maxTasksPerProcess < 1) maxTasksPerProcess = 200;
        }
    }
}
