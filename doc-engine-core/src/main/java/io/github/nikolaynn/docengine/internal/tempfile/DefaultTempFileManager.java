package io.github.nikolaynn.docengine.internal.tempfile;

import io.github.nikolaynn.docengine.api.exception.TempFileException;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DefaultTempFileManager implements TempFileManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTempFileManager.class);

    private final Path rootDir;
    private final Set<Path> tracked = new CopyOnWriteArraySet<>();
    private final Thread shutdownHook;

    public DefaultTempFileManager(Path rootDir, boolean cleanupOnShutdown) {
        this.rootDir = rootDir;
        if (rootDir != null) {
            try {
                Files.createDirectories(rootDir);
            } catch (IOException e) {
                throw new TempFileException(null, null, null,
                    "failed to create temp root directory " + rootDir, e);
            }
        }
        if (cleanupOnShutdown) {
            this.shutdownHook = new Thread(this::cleanupAll, "doc-engine-temp-cleanup");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else {
            this.shutdownHook = null;
        }
    }

    @Override
    public void close() {
        cleanupAll();
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down; the hook will run (or has run) anyway
            }
        }
    }

    @Override
    public Path createTempFile(String prefix, String suffix) {
        try {
            Path file = rootDir == null
                ? Files.createTempFile(prefix, suffix)
                : Files.createTempFile(rootDir, prefix, suffix);
            tracked.add(file);
            return file;
        } catch (IOException e) {
            throw new TempFileException(null, null, null,
                "failed to create temp file in " + (rootDir == null ? "<system tmp>" : rootDir), e);
        }
    }

    @Override
    public void delete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
            tracked.remove(path);
        } catch (IOException e) {
            log.warn("failed to delete temp file {}: {}", path, e.getMessage());
        }
    }

    private void cleanupAll() {
        for (Path p : tracked) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // shutdown best-effort
            }
        }
    }
}
