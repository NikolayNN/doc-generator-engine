package com.example.docengine.starter;

import com.example.docengine.api.DocumentEngine;
import com.example.docengine.spi.DocumentConverter;
import com.example.docengine.spi.TempFileManager;
import com.example.docengine.spi.TemplateResolver;
import com.example.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
