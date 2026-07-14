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
            converter = new Converter(new LibreOffice(true, null, null, null));
        }
    }

    public record Converter(LibreOffice libreoffice) {
        public Converter {
            if (libreoffice == null) {
                libreoffice = new LibreOffice(true, null, null, null);
            }
        }
    }

    public record LibreOffice(boolean enabled, Path executable, Duration timeout, Path workingDir) {
        public LibreOffice {
            if (timeout == null) timeout = Duration.ofSeconds(60);
        }
    }
}
