package io.github.nikolaynn.docengine.starter;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocEngineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JodConverterAutoConfiguration.class, DocEngineAutoConfiguration.class));

    @Test
    void defaultsAllBeansPresent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DocumentEngine.class);
            assertThat(ctx).hasSingleBean(TempFileManager.class);
            assertThat(ctx).hasSingleBean(TemplateResolver.class);
            assertThat(ctx).hasSingleBean(TemplateValidator.class);
            assertThat(ctx).hasBean("jxlsTemplateEngine");
            assertThat(ctx).hasBean("jodDocumentConverter");
            assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
        });
    }

    @Test
    void jodDisabledFallsBackToProcessConverter() {
        runner.withPropertyValues("doc-engine.converter.jod.enabled=false")
              .run(ctx -> {
                  assertThat(ctx).doesNotHaveBean("jodDocumentConverter");
                  assertThat(ctx).hasBean("libreOfficeConverter");
              });
    }

    @Test
    void bothConvertersCanBeDisabled() {
        runner.withPropertyValues(
                "doc-engine.converter.jod.enabled=false",
                "doc-engine.converter.libreoffice.enabled=false")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(DocumentEngine.class);
                  assertThat(ctx.getBeansOfType(DocumentConverter.class)).isEmpty();
              });
    }

    @Test
    void userProvidedDocumentEngineWins() {
        runner.withUserConfiguration(UserEngineConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(DocumentEngine.class);
            assertThat(ctx.getBean(DocumentEngine.class))
                .isSameAs(ctx.getBean("userEngine"));
        });
    }

    @Test
    void userConverterSuppressesAllDefaultConverters() {
        runner.withUserConfiguration(UserConverterConfig.class).run(ctx -> {
            assertThat(ctx.getBeansOfType(DocumentConverter.class)).hasSize(1);
            assertThat(ctx).doesNotHaveBean("jodDocumentConverter");
            assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
        });
    }

    @Test
    void jodPropertiesBind() {
        runner.withPropertyValues(
            "doc-engine.converter.jod.pool-size=3",
            "doc-engine.converter.jod.task-timeout=90s",
            "doc-engine.converter.jod.max-tasks-per-process=50"
        ).run(ctx -> {
            DocEngineProperties p = ctx.getBean(DocEngineProperties.class);
            assertThat(p.converter().jod().poolSize()).isEqualTo(3);
            assertThat(p.converter().jod().taskTimeout().toSeconds()).isEqualTo(90);
            assertThat(p.converter().jod().maxTasksPerProcess()).isEqualTo(50);
        });
    }

    @Test
    void propertiesBind() {
        runner.withPropertyValues(
            "doc-engine.temp-dir=/tmp/de",
            "doc-engine.cleanup-on-shutdown=false",
            "doc-engine.converter.libreoffice.executable=/opt/soffice",
            "doc-engine.converter.libreoffice.timeout=15s"
        ).run(ctx -> {
            DocEngineProperties p = ctx.getBean(DocEngineProperties.class);
            assertThat(p.tempDir().toString()).endsWith("de");
            assertThat(p.cleanupOnShutdown()).isFalse();
            assertThat(p.converter().libreoffice().executable().toString()).endsWith("soffice");
            assertThat(p.converter().libreoffice().timeout().toSeconds()).isEqualTo(15);
        });
    }

    @Test
    void cleanupOnShutdownDefaultsToTrue() {
        runner.run(ctx -> {
            DocEngineProperties p = ctx.getBean(DocEngineProperties.class);
            assertThat(p.cleanupOnShutdown())
                .as("cleanup-on-shutdown must default to true, matching plain-Java withDefaults()")
                .isTrue();
        });
    }

    @Test
    void registeredViaAutoConfigurationImportsForBoot3() {
        ImportCandidates candidates =
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates)
            .as("Boot 3 loads auto-configurations only from META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .contains(DocEngineAutoConfiguration.class.getName());
        assertThat(candidates).contains(JodConverterAutoConfiguration.class.getName());
    }

    @Test
    void contextCloseCleansUpTrackedTempFiles(@TempDir Path tmp) {
        AtomicReference<Path> leftover = new AtomicReference<>();
        runner.withPropertyValues("doc-engine.temp-dir=" + tmp)
              .run(ctx -> {
                  leftover.set(ctx.getBean(TempFileManager.class).createTempFile("ctx-", ".tmp"));
                  assertThat(leftover.get()).exists();
              });
        // ApplicationContextRunner closes the context after the callback
        assertThat(leftover.get())
            .as("temp files must be cleaned up on context close, not only at JVM exit")
            .doesNotExist();
    }

    @Test
    void closingEngineBeanDoesNotCloseSharedCollaboratorBeans() {
        runner.withUserConfiguration(RecordingTempFileManagerConfig.class).run(ctx -> {
            RecordingTempFileManager tfm = ctx.getBean(RecordingTempFileManager.class);
            // a caller closes the injected engine (try-with-resources, own cleanup) while
            // the context is still live: the shared collaborator beans must survive.
            ((DocumentEngine) ctx.getBean(DocumentEngine.class)).close();
            assertThat(tfm.closed)
                .as("closing the engine bean must not close the shared temp-file-manager bean")
                .isFalse();
        });
    }

    @Test
    void contextFailsToStartWhenNoTemplateEnginesPresent() {
        runner.withUserConfiguration(SuppressJxlsConfig.class)
              .run(ctx -> assertThat(ctx)
                  .as("an engine with zero template engines must fail at startup, not on first generate()")
                  .hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class SuppressJxlsConfig {
        // a bean with this name suppresses the default via @ConditionalOnMissingBean(name=...)
        // without contributing a TemplateEngine
        @Bean(name = "jxlsTemplateEngine")
        String jxlsTemplateEngine() { return "suppressed"; }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserEngineConfig {
        @Bean DocumentEngine userEngine() { return mock(DocumentEngine.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserConverterConfig {
        @Bean
        DocumentConverter userConverter() { return mock(DocumentConverter.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class RecordingTempFileManagerConfig {
        @Bean
        RecordingTempFileManager tempFileManager() { return new RecordingTempFileManager(); }
    }

    /** Records whether close() was invoked, so a premature engine.close() is observable. */
    static class RecordingTempFileManager implements TempFileManager {
        volatile boolean closed;
        @Override public Path createTempFile(String prefix, String suffix) {
            throw new UnsupportedOperationException();
        }
        @Override public void delete(Path path) {}
        @Override public void close() { closed = true; }
    }
}
