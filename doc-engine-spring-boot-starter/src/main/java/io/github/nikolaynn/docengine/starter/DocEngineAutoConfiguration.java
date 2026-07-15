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

/**
 * Auto-configuration for the doc-engine core beans (template engine, resolver, validator,
 * temp-file manager, document engine) and the process-based LibreOffice converter.
 *
 * <p><strong>Converter selection.</strong> The two built-in converters — the pooled
 * {@code jodDocumentConverter} (present when the doc-engine-jodconverter module is on the
 * classpath) and the process-based {@link #libreOfficeConverter} — both provide XLSX&rarr;PDF
 * and are mutually exclusive: jod is preferred, and the LibreOffice converter is created only
 * as a fallback when jod is absent (module missing or {@code doc-engine.converter.jod.enabled=false}).
 *
 * <p>Both are guarded by {@code @ConditionalOnMissingBean(DocumentConverter.class)}, so
 * <em>registering any {@code DocumentConverter} bean of your own replaces BOTH built-ins</em>.
 * This is intentional: it prevents two converters competing for the same conversion, so a
 * user-supplied converter takes over entirely. To keep the built-in XLSX&rarr;PDF while also
 * adding a converter for another format pair, declare the converters yourself (e.g. re-declare
 * {@code libreOfficeConverter}/{@code jodDocumentConverter} as your own beans alongside the new
 * one) or assemble the engine directly with {@code DocumentEngineBuilder}.
 */
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
    @ConditionalOnMissingBean(DocumentConverter.class)
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
        // closeDelegates=false: the converters and temp-file manager are their own Spring
        // beans, closed by the container on context shutdown. The engine must not close
        // them itself, or a caller closing the engine bean (or a redundant shutdown pass)
        // would tear down collaborators still in use.
        return new DefaultDocumentEngine(engines, converters, resolver, validator, tempFiles, false);
    }
}
