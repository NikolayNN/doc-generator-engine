# Cluster B DX/Interface Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Улучшить эргономику и честность публичного API: builder для `GenerationOptions`, фабрики `TemplateReference`, Spring config metadata, Javadoc — и явно задокументировать `locale`/`engineHints`/`timeout` как advisory SPI-хуки.

**Architecture:** Только добавления к публичному API core и стартеру. Никаких изменений поведения: встроенный JXLS-движок по-прежнему не применяет `locale`/`engineHints`; `timeout` honor'ится только процессным LibreOffice-конвертером. Все канонические конструкторы и публичные записи сохраняются.

**Tech Stack:** Java 17, Maven multi-module, JUnit 5 + AssertJ, Spring Boot config-processor (уже подключён в стартере).

**Spec:** `docs/superpowers/specs/2026-07-14-cluster-b-dx-polish-design.md`

## Global Constraints

- Java 17 (`maven.compiler.source/target=17`).
- Пакеты — `io.github.nikolaynn.docengine.*`.
- **Сборка (критично):** локальный `JAVA_HOME` указывает на JDK 11 → mvn падает с «invalid target release: 17». Перед КАЖДЫМ mvn в PowerShell: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn ...`.
- TDD для задач 1–3 (тест пишется и падает ДО кода; ошибка компиляции — валидный RED). Задачи 4–5 — документация (README/Javadoc), без RED/GREEN-цикла; корректность = зелёный `mvn -B verify`.
- Только добавления, обратная совместимость: канонический конструктор `GenerationOptions`, `GenerationOptions.defaults()`, записи `TemplateReference.BytesRef`/`InputStreamRef` НЕ меняются.
- `spring-boot-configuration-processor` УЖЕ есть в `doc-engine-spring-boot-starter/pom.xml` (optional) — повторно НЕ добавлять.
- Каждая задача заканчивается коммитом с трейлером `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- `mvn -B verify` в корне зелёный в конце каждой задачи (BUILD SUCCESS, «All coverage checks have been met»; гейтованные LibreOffice-IT скипаются локально — это норма).

---

### Task 1: GenerationOptions builder + advisory Javadoc

**Files:**
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/GenerationOptions.java`
- Test: `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/GenerationOptionsTest.java` (создать)

**Interfaces:**
- Consumes: существующий record `GenerationOptions(String fileNameHint, Duration timeout, Locale locale, Map<String,Object> engineHints)` с compact-конструктором (нормализует `engineHints`) и `defaults()`.
- Produces: `GenerationOptions.builder()` → `GenerationOptions.Builder` c `fileNameHint(String)`, `timeout(Duration)`, `locale(Locale)`, `engineHint(String,Object)`, `engineHints(Map)`, `build()`.

- [ ] **Step 1: Написать падающий тест**

Создать `GenerationOptionsTest.java` целиком:

```java
package io.github.nikolaynn.docengine.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class GenerationOptionsTest {

    @Test
    void builderMatchesCanonicalConstructor() {
        var built = GenerationOptions.builder()
            .fileNameHint("report")
            .timeout(Duration.ofSeconds(30))
            .locale(Locale.GERMANY)
            .engineHints(Map.of("k", "v"))
            .build();
        var canonical = new GenerationOptions("report", Duration.ofSeconds(30),
            Locale.GERMANY, Map.of("k", "v"));
        assertThat(built).isEqualTo(canonical);
    }

    @Test
    void engineHintAccumulatesInInsertionOrder() {
        var opts = GenerationOptions.builder()
            .engineHint("a", 1)
            .engineHint("b", 2)
            .build();
        assertThat(opts.engineHints()).containsExactly(entry("a", 1), entry("b", 2));
    }

    @Test
    void engineHintsReplacesAccumulatedHints() {
        var opts = GenerationOptions.builder()
            .engineHint("a", 1)
            .engineHints(Map.of("b", 2))
            .build();
        assertThat(opts.engineHints()).containsOnlyKeys("b");
    }

    @Test
    void emptyBuilderEqualsDefaults() {
        assertThat(GenerationOptions.builder().build())
            .isEqualTo(GenerationOptions.defaults());
    }

    @Test
    void nullSettersAreTolerated() {
        var opts = GenerationOptions.builder()
            .fileNameHint(null).timeout(null).locale(null).engineHints(null)
            .build();
        assertThat(opts.fileNameHint()).isNull();
        assertThat(opts.timeout()).isNull();
        assertThat(opts.locale()).isNull();
        assertThat(opts.engineHints()).isEmpty();
    }
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-core test "-Dtest=GenerationOptionsTest"`
Expected: COMPILATION ERROR — `cannot find symbol: method builder()`.

- [ ] **Step 3: Реализация**

`GenerationOptions.java` — целиком:

```java
package io.github.nikolaynn.docengine.api;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Options for a single generation. All fields are optional; use {@link #builder()}
 * for readable construction or {@link #defaults()} for none.
 *
 * <p>Some options are advisory: the bundled components do not all honor them, but
 * every option is passed to the SPI contexts, so custom
 * {@link io.github.nikolaynn.docengine.spi.TemplateEngine} and
 * {@link io.github.nikolaynn.docengine.spi.DocumentConverter} implementations may.
 *
 * @param fileNameHint base name for the produced file (the extension is appended
 *        when missing); when {@code null} or blank a name is derived from the
 *        template hint
 * @param timeout conversion timeout; honored ONLY by the process-based LibreOffice
 *        converter. The JXLS renderer and the JODConverter pool ignore it (the
 *        pool applies its own configured task timeout)
 * @param locale advisory: the bundled JXLS engine does not apply it; a custom
 *        {@code TemplateEngine} receives it via
 *        {@link io.github.nikolaynn.docengine.spi.RenderContext} and may honor it
 * @param engineHints advisory generic pass-through: the bundled components ignore
 *        these; custom engines/converters receive them via the SPI contexts
 */
public record GenerationOptions(
        String fileNameHint,
        Duration timeout,
        Locale locale,
        Map<String, Object> engineHints
) {
    private static final GenerationOptions DEFAULTS =
            new GenerationOptions(null, null, null, Map.of());

    public GenerationOptions {
        // null-tolerant copy: Map.copyOf rejects null values
        engineHints = engineHints == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(engineHints));
    }

    /** Options with nothing set. */
    public static GenerationOptions defaults() {
        return DEFAULTS;
    }

    /** A fresh builder; all fields start unset. */
    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder for {@link GenerationOptions}. */
    public static final class Builder {
        private String fileNameHint;
        private Duration timeout;
        private Locale locale;
        private Map<String, Object> engineHints;

        private Builder() {}

        public Builder fileNameHint(String v) { this.fileNameHint = v; return this; }

        public Builder timeout(Duration v) { this.timeout = v; return this; }

        public Builder locale(Locale v) { this.locale = v; return this; }

        /** Replaces the accumulated hints ({@code null} clears them). */
        public Builder engineHints(Map<String, Object> hints) {
            this.engineHints = hints == null ? null : new LinkedHashMap<>(hints);
            return this;
        }

        /** Adds or overwrites a single hint, preserving insertion order. */
        public Builder engineHint(String key, Object value) {
            if (engineHints == null) {
                engineHints = new LinkedHashMap<>();
            }
            engineHints.put(key, value);
            return this;
        }

        public GenerationOptions build() {
            return new GenerationOptions(fileNameHint, timeout, locale, engineHints);
        }
    }
}
```

- [ ] **Step 4: Тесты зелёные**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-core test "-Dtest=GenerationOptionsTest"`
Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 5: Полный прогон и коммит**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B verify` → BUILD SUCCESS.

```bash
git add doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/GenerationOptions.java doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/GenerationOptionsTest.java
git commit -m "feat: GenerationOptions builder and advisory option docs"
```
(Сообщение коммита завершить трейлером `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.)

---

### Task 2: TemplateReference factory methods

**Files:**
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/TemplateReference.java`
- Test: `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/TemplateReferenceTest.java` (дополнить)

**Interfaces:**
- Consumes: интерфейс `TemplateReference` с записями `BytesRef(byte[] bytes, DocumentFormat sourceFormat, String hint)` (уже value-based equals/hashCode) и `InputStreamRef(InputStream stream, DocumentFormat sourceFormat, String hint)`.
- Produces: `TemplateReference.ofBytes(byte[], DocumentFormat, String)` и `TemplateReference.ofStream(InputStream, DocumentFormat, String)`, оба возвращают тип интерфейса.

- [ ] **Step 1: Написать падающий тест**

Добавить в `TemplateReferenceTest` (перед приватными хелперами/последним методом) два теста:

```java
    @Test
    void ofBytesBuildsEqualBytesRef() {
        byte[] payload = {1, 2, 3};
        assertThat(TemplateReference.ofBytes(payload, DocumentFormat.XLSX, "r"))
            .isInstanceOf(TemplateReference.BytesRef.class)
            .isEqualTo(new TemplateReference.BytesRef(payload, DocumentFormat.XLSX, "r"));
    }

    @Test
    void ofStreamBuildsInputStreamRefWithSameFields() {
        InputStream in = new ByteArrayInputStream(new byte[]{1});
        var ref = TemplateReference.ofStream(in, DocumentFormat.XLSX, "tpl");
        assertThat(ref).isInstanceOf(TemplateReference.InputStreamRef.class);
        var isr = (TemplateReference.InputStreamRef) ref;
        assertThat(isr.stream()).isSameAs(in);
        assertThat(isr.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(isr.hint()).isEqualTo("tpl");
    }
```

(`InputStream`, `ByteArrayInputStream`, `assertThat` уже импортированы в этом файле.)

- [ ] **Step 2: Убедиться, что тест падает**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-core test "-Dtest=TemplateReferenceTest"`
Expected: COMPILATION ERROR — `cannot find symbol: method ofBytes(...)`.

- [ ] **Step 3: Реализация**

В `TemplateReference.java` добавить два static-метода в тело интерфейса (например, сразу после `String hint();`, перед объявлением `record InputStreamRef`):

```java
    /** A reference to in-memory template bytes. */
    static TemplateReference ofBytes(byte[] bytes, DocumentFormat sourceFormat, String hint) {
        return new BytesRef(bytes, sourceFormat, hint);
    }

    /**
     * A reference to a template stream. The stream is consumed once during
     * resolution, so a reference is single-use.
     */
    static TemplateReference ofStream(InputStream stream, DocumentFormat sourceFormat, String hint) {
        return new InputStreamRef(stream, sourceFormat, hint);
    }
```

- [ ] **Step 4: Тесты зелёные**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-core test "-Dtest=TemplateReferenceTest"`
Expected: `Tests run: 10, Failures: 0, Errors: 0` (8 существующих + 2 новых).

- [ ] **Step 5: Полный прогон и коммит**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B verify` → BUILD SUCCESS.

```bash
git add doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/TemplateReference.java doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/TemplateReferenceTest.java
git commit -m "feat: TemplateReference.ofBytes/ofStream factory methods"
```
(Трейлер `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.)

---

### Task 3: Spring config metadata

**Files:**
- Modify: `doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/DocEngineProperties.java` (Javadoc `@param` на записях)
- Create: `doc-engine-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Test: `doc-engine-spring-boot-starter/src/test/java/io/github/nikolaynn/docengine/starter/ConfigMetadataTest.java` (создать)

**Interfaces:**
- Consumes: существующий `DocEngineProperties` (записи `DocEngineProperties`, `Converter`, `LibreOffice`, `Jod`), `spring-boot-configuration-processor` (уже в pom, optional) — на этапе компиляции генерит `target/classes/META-INF/spring-configuration-metadata.json` и мёржит в него `additional-spring-configuration-metadata.json`.
- Produces: ничего кодового; IDE-metadata для `doc-engine.*`.

- [ ] **Step 1: Написать падающий guard-тест**

Создать `ConfigMetadataTest.java` целиком (dependency-free парсинг — только `InputStream`/`String`, без Jackson):

```java
package io.github.nikolaynn.docengine.starter;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMetadataTest {

    @Test
    void generatedMetadataListsPropertiesWithMergedDefaults() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring-configuration-metadata.json")) {
            assertThat(in).as("config-processor generated metadata on classpath").isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // properties emitted from DocEngineProperties by the processor
        assertThat(json)
            .contains("doc-engine.temp-dir")
            .contains("doc-engine.cleanup-on-shutdown")
            .contains("doc-engine.converter.libreoffice.timeout")
            .contains("doc-engine.converter.jod.pool-size")
            .contains("doc-engine.converter.jod.max-tasks-per-process");
        // a default merged from additional-spring-configuration-metadata.json:
        // "120s" is the jod.task-timeout default and cannot be inferred by the
        // processor, so it is present ONLY after the additional file is added
        assertThat(json).contains("120s");
    }
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-spring-boot-starter -am test "-Dtest=ConfigMetadataTest"`
Expected: FAIL on the `.contains("120s")` assertion — the property names are already emitted by the processor, but no `defaultValue` (`120s`) exists until the additional file is added.

(Если по какой-то причине сгенерированный файл вообще отсутствует и падает первый ассерт `isNotNull` — это тоже валидный RED; после Step 3 он станет зелёным.)

- [ ] **Step 3: Реализация**

3a. `DocEngineProperties.java` — целиком (добавлен Javadoc `@param` на каждой записи; процессор превращает их в `description`):

```java
package io.github.nikolaynn.docengine.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration for the doc-engine Spring Boot starter, bound from {@code doc-engine.*}.
 *
 * @param tempDir directory for temporary files; {@code null} uses the system temp dir
 * @param cleanupOnShutdown delete tracked temp files on JVM shutdown
 * @param converter converter configuration
 */
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
                jod = new Jod(true, null, 1, null, null, 200);
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
     * @param taskTimeout per-task execution timeout
     * @param taskQueueTimeout how long a task waits for a free process
     * @param maxTasksPerProcess restart a process after this many tasks
     */
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
```

3b. Создать `doc-engine-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json` целиком (дефолты, которые процессор не выводит из compact-конструкторов):

```json
{
  "properties": [
    { "name": "doc-engine.cleanup-on-shutdown", "type": "java.lang.Boolean", "defaultValue": true },
    { "name": "doc-engine.converter.libreoffice.enabled", "type": "java.lang.Boolean", "defaultValue": true },
    { "name": "doc-engine.converter.libreoffice.timeout", "type": "java.time.Duration", "defaultValue": "60s" },
    { "name": "doc-engine.converter.jod.enabled", "type": "java.lang.Boolean", "defaultValue": true },
    { "name": "doc-engine.converter.jod.pool-size", "type": "java.lang.Integer", "defaultValue": 1 },
    { "name": "doc-engine.converter.jod.task-timeout", "type": "java.time.Duration", "defaultValue": "120s" },
    { "name": "doc-engine.converter.jod.task-queue-timeout", "type": "java.time.Duration", "defaultValue": "30s" },
    { "name": "doc-engine.converter.jod.max-tasks-per-process", "type": "java.lang.Integer", "defaultValue": 200 }
  ]
}
```

- [ ] **Step 4: Тест зелёный**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-spring-boot-starter -am test "-Dtest=ConfigMetadataTest"`
Expected: `Tests run: 1, Failures: 0, Errors: 0`.

Примечание для исполнителя: тест читает СГЕНЕРИРОВАННЫЙ `target/classes/META-INF/spring-configuration-metadata.json` (процессор кладёт его туда при компиляции и мёржит туда additional-файл). Если ассерт про конкретное имя свойства не сойдётся из-за формата (процессор пишет каноническое kebab-имя `doc-engine.converter.jod.pool-size`) — сверить фактическое содержимое файла и поправить ассерт под реальные имена, зафиксировав в отчёте. Значения `defaultValue` в merged-файле могут быть без пробела (`"defaultValue":1`) — тест проверяет только подстроку `defaultValue`, так что формат неважен.

- [ ] **Step 5: Полный прогон и коммит**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B verify` → BUILD SUCCESS.

```bash
git add doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/DocEngineProperties.java doc-engine-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json doc-engine-spring-boot-starter/src/test/java/io/github/nikolaynn/docengine/starter/ConfigMetadataTest.java
git commit -m "feat: Spring config metadata for doc-engine properties"
```
(Трейлер `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.)

---

### Task 4: Javadoc на остальном публичном API

**Files:**
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/DocumentEngineBuilder.java`
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/GenerationRequest.java`
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/GenerationResult.java`
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/GenerationMetadata.java`
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/DocumentFormat.java`
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/exception/DocumentGenerationException.java`

**Interfaces:**
- Consumes: существующие публичные типы (сигнатуры не меняются).
- Produces: только Javadoc. Никаких новых символов.

Это задача-документация: TDD-цикла нет. Каждый шаг — добавить class-level Javadoc на существующее объявление (тело классов не трогать). Проверка — компиляция в `mvn -B verify`.

- [ ] **Step 1: `DocumentEngineBuilder` — class Javadoc**

Добавить НАД `public final class DocumentEngineBuilder {`:

```java
/**
 * Fluent builder for a plain-Java {@link DocumentEngine}. Start with
 * {@link #create()}, add components (or {@link #withDefaults()}), then
 * {@link #build()}. At least one template engine and a temp-file manager are
 * required; a resolver and validator default to no-op implementations.
 */
```

- [ ] **Step 2: `GenerationRequest` — record Javadoc**

Добавить НАД `public record GenerationRequest(`:

```java
/**
 * A single generation request.
 *
 * @param template the template to render (bytes, stream, or a custom reference)
 * @param data the data model exposed to the template; may contain null values,
 *        {@code null} is treated as empty
 * @param targetFormat the desired output format (a conversion runs when it differs
 *        from the template's source format)
 * @param options generation options; {@code null} means {@link GenerationOptions#defaults()}
 */
```

- [ ] **Step 3: `GenerationResult` — record Javadoc**

Добавить НАД `public record GenerationResult(`:

```java
/**
 * A fully buffered generation result.
 *
 * @param fileName suggested file name (with extension)
 * @param mimeType MIME type of {@link #content()}
 * @param format the produced document format
 * @param content the produced document bytes
 */
```

- [ ] **Step 4: `GenerationMetadata` — record Javadoc**

Открыть файл, добавить НАД объявлением `public record GenerationMetadata(`:

```java
/**
 * Metadata about a streamed generation (the bytes go to the caller's
 * {@code OutputStream}/file, so only the descriptors are returned).
 *
 * @param fileName suggested file name (with extension)
 * @param mimeType MIME type of the produced document
 * @param format the produced document format
 */
```

- [ ] **Step 5: `DocumentFormat` — enum + constant Javadoc**

Добавить НАД `public enum DocumentFormat {`:

```java
/** Supported document formats, each carrying its MIME type and file extension. */
```

- [ ] **Step 6: `DocumentGenerationException` — class Javadoc**

Открыть `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/exception/DocumentGenerationException.java` и добавить НАД объявлением класса:

```java
/**
 * Root of the library's unchecked exception hierarchy. Internal JXLS/POI/I-O
 * failures never leak: they are wrapped into a subtype of this class. Each
 * exception carries a template hint and the source/target formats; template
 * content and data values are never included (PII-safe).
 */
```

- [ ] **Step 7: Полный прогон и коммит**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B verify` → BUILD SUCCESS (Javadoc — валидные комментарии, компиляция проходит; строгого javadoc-линта в сборке нет).

```bash
git add doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api
git commit -m "docs: Javadoc across the public API surface"
```
(Трейлер `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.)

Примечание: если у `DocumentGenerationException` конструкторы/поля отличаются от ожидаемого — не менять сигнатуры, только добавить class-level Javadoc из Step 6. Если класс уже имеет class-level Javadoc — дополнить/заменить, не дублируя.

---

### Task 5: README и финальная верификация

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: всё из задач 1–3.
- Produces: документацию; ничего кодового.

- [ ] **Step 1: Обновить README**

1a. В секции «Быстрый старт — plain Java», после существующего примера с `new GenerationRequest(... GenerationOptions.defaults())`, добавить абзац:

```markdown
Опции и ссылки на шаблон можно собирать эргономичнее:

```java
GenerationOptions opts = GenerationOptions.builder()
    .fileNameHint("invoice")
    .timeout(Duration.ofSeconds(60))
    .build();

TemplateReference ref = TemplateReference.ofBytes(templateBytes, DocumentFormat.XLSX, "invoice");
// или из потока: TemplateReference.ofStream(in, DocumentFormat.XLSX, "invoice")
```

`GenerationOptions` — advisory-контракт: `timeout` применяет **только** процессный
LibreOffice-конвертер (JXLS-рендер и пул JODConverter его игнорируют); `locale` и
`engineHints` встроенный JXLS-движок не использует — их получают лишь кастомные
`TemplateEngine`/`DocumentConverter` через SPI-контексты.
```

1b. Проверить, что раздел про Spring-конфигурацию (yaml) уже перечисляет `doc-engine.converter.jod.*` (добавлено ранее). Дополнительных правок yaml не требуется — IDE-автодополнение теперь обеспечивается config-metadata из задачи 3. Если хочется — одну строку в конце yaml-примера:

```markdown
> Свойства `doc-engine.*` снабжены config-metadata: IDE подсказывает ключи, типы и значения по умолчанию.
```

- [ ] **Step 2: Финальная верификация**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B verify`
Expected: BUILD SUCCESS, все модули зелёные, «All coverage checks have been met».

- [ ] **Step 3: Коммит**

```bash
git add README.md
git commit -m "docs: document GenerationOptions builder, template factories and config metadata"
```
(Трейлер `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.)

После завершения плана: пуш и контроль CI — по команде пользователя.
