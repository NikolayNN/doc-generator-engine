package io.github.nikolaynn.docengine.starter;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocEngineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DocEngineAutoConfiguration.class));

    @Test
    void defaultsAllBeansPresent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DocumentEngine.class);
            assertThat(ctx).hasSingleBean(TempFileManager.class);
            assertThat(ctx).hasSingleBean(TemplateResolver.class);
            assertThat(ctx).hasSingleBean(TemplateValidator.class);
            assertThat(ctx).hasBean("jxlsTemplateEngine");
            assertThat(ctx).hasBean("libreOfficeConverter");
        });
    }

    @Test
    void libreOfficeConverterCanBeDisabledViaProperty() {
        runner.withPropertyValues("doc-engine.converter.libreoffice.enabled=false")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(DocumentEngine.class);
                  assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
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
    void userConverterReplacesDefault() {
        runner.withUserConfiguration(UserConverterConfig.class).run(ctx -> {
            assertThat(ctx.getBeansOfType(DocumentConverter.class).values())
                .anyMatch(c -> c == ctx.getBean("libreOfficeConverter"));
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
    void registeredViaAutoConfigurationImportsForBoot3() {
        ImportCandidates candidates =
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates)
            .as("Boot 3 loads auto-configurations only from META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .contains(DocEngineAutoConfiguration.class.getName());
    }

    @Test
    void registeredViaSpringFactoriesForBoot27() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(
                EnableAutoConfiguration.class, getClass().getClassLoader()))
            .contains(DocEngineAutoConfiguration.class.getName());
    }

    @Configuration(proxyBeanMethods = false)
    static class UserEngineConfig {
        @Bean DocumentEngine userEngine() { return mock(DocumentEngine.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserConverterConfig {
        @Bean(name = "libreOfficeConverter")
        DocumentConverter userConverter() { return mock(DocumentConverter.class); }
    }
}
