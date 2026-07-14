package io.github.nikolaynn.docengine.starter;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.internal.DefaultDocumentEngine;
import io.github.nikolaynn.docengine.internal.jxls.JxlsTemplateEngine;
import io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverter;
import io.github.nikolaynn.docengine.internal.resolver.InputStreamTemplateResolver;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.internal.validator.NoopTemplateValidator;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocEngineProperties.class)
public class DocEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TempFileManager tempFileManager(DocEngineProperties props) {
        return new DefaultTempFileManager(props.tempDir(), props.cleanupOnShutdown());
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateResolver templateResolver() {
        return new InputStreamTemplateResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateValidator templateValidator() {
        return new NoopTemplateValidator();
    }

    @Bean(name = "jxlsTemplateEngine")
    @ConditionalOnMissingBean(name = "jxlsTemplateEngine")
    public TemplateEngine jxlsTemplateEngine() {
        return new JxlsTemplateEngine();
    }

    @Bean(name = "libreOfficeConverter")
    @ConditionalOnMissingBean(name = "libreOfficeConverter")
    @ConditionalOnProperty(prefix = "doc-engine.converter.libreoffice",
                           name = "enabled", havingValue = "true", matchIfMissing = true)
    public DocumentConverter libreOfficeConverter(DocEngineProperties props) {
        var lo = props.converter().libreoffice();
        return new LibreOfficeConverter(lo.executable(), lo.timeout(), lo.workingDir());
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentEngine documentEngine(List<TemplateEngine> engines,
                                         List<DocumentConverter> converters,
                                         TemplateResolver resolver,
                                         TemplateValidator validator,
                                         TempFileManager tempFiles) {
        return new DefaultDocumentEngine(engines, converters, resolver, validator, tempFiles);
    }
}
