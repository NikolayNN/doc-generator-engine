package io.github.nikolaynn.docengine.starter;

import io.github.nikolaynn.docengine.jod.JodDocumentConverter;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Активируется, когда doc-engine-jodconverter есть на classpath: пул
 * LibreOffice-процессов становится основным конвертером, процессный
 * конвертер из DocEngineAutoConfiguration отступает (его условие —
 * отсутствие других DocumentConverter-бинов).
 */
@AutoConfiguration(before = DocEngineAutoConfiguration.class)
@ConditionalOnClass(JodDocumentConverter.class)
@EnableConfigurationProperties(DocEngineProperties.class)
public class JodConverterAutoConfiguration {

    @Bean(name = "jodDocumentConverter")
    @ConditionalOnMissingBean(DocumentConverter.class)
    @ConditionalOnProperty(prefix = "doc-engine.converter.jod",
                           name = "enabled", havingValue = "true", matchIfMissing = true)
    public DocumentConverter jodDocumentConverter(DocEngineProperties props) {
        var jod = props.converter().jod();
        return new JodDocumentConverter(JodDocumentConverter.Config.builder()
            .officeHome(jod.officeHome())
            .poolSize(jod.poolSize())
            .taskTimeout(jod.taskTimeout())
            .taskQueueTimeout(jod.taskQueueTimeout())
            .maxTasksPerProcess(jod.maxTasksPerProcess())
            .build());
    }
}
