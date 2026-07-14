# JODConverter LibreOffice Pool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Устранить cold start LibreOffice (2–6 с на конверсию): новый модуль `doc-engine-jodconverter` с пулом долгоживущих процессов через JODConverter, автоактивация в Spring-стартере.

**Architecture:** Отдельный Maven-модуль с единственным публичным классом `JodDocumentConverter` (SPI `DocumentConverter`), оборачивающим `LocalOfficeManager` из jodconverter-local 4.4.9. Пул и сам `LocalOfficeManager` создаются лениво при первой конверсии (builder JODConverter падает без установленного soffice — поэтому в конструкторе только сохраняем конфиг). Core получает каскад `close()` на конвертеры; стартер — новую автоконфигурацию, активную по classpath.

**Tech Stack:** Java 17, Maven multi-module, JODConverter 4.4.9 (`org.jodconverter:jodconverter-local`), JUnit 5 + Mockito + AssertJ, Spring Boot 2.7 автоконфигурация (spring.factories + AutoConfiguration.imports).

**Spec:** `docs/superpowers/specs/2026-07-14-jodconverter-pool-design.md`

## Global Constraints

- Java 17 (`maven.compiler.source/target=17` из корневого pom).
- Пакеты — `io.github.nikolaynn.docengine.*`; новый модуль — пакет `io.github.nikolaynn.docengine.jod`.
- Сборка: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'` перед каждым mvn (локальный JAVA_HOME указывает на JDK 11).
- TDD: тест пишется и падает ДО продакшен-кода; ошибка компиляции — валидный RED.
- Каждая задача заканчивается коммитом с трейлером `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- `mvn -B verify` в корне должен быть зелёным в конце каждой задачи.
- Юнит-тесты не требуют установленного LibreOffice; интеграционные — под гейтом `@EnabledIf("sofficeAvailable")`.

---

### Task 1: Каскад close() на конвертеры в core

**Files:**
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/spi/DocumentConverter.java`
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/internal/DefaultDocumentEngine.java` (метод `close()`, сейчас `close() { tempFiles.close(); }`)
- Test: `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/internal/DefaultDocumentEngineTest.java`

**Interfaces:**
- Consumes: `TempFileManager.close()` (default no-op, уже существует).
- Produces: `DocumentConverter extends AutoCloseable` c `default void close() {}` — Task 2 реализует его в `JodDocumentConverter`; `DefaultDocumentEngine.close()` закрывает все конвертеры, затем tempFiles.

- [ ] **Step 1: Написать падающий тест**

В `DefaultDocumentEngineTest` добавить импорт `java.util.concurrent.atomic.AtomicBoolean` и тест:

```java
@Test
void closeClosesConvertersAndTempFileManager() {
    AtomicBoolean converterClosed = new AtomicBoolean();
    AtomicBoolean tfmClosed = new AtomicBoolean();
    DocumentConverter converter = new DocumentConverter() {
        @Override public boolean supports(DocumentFormat from, DocumentFormat to) { return false; }
        @Override public Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx) {
            throw new UnsupportedOperationException();
        }
        @Override public void close() { converterClosed.set(true); }
    };
    TempFileManager manager = new TempFileManager() {
        @Override public Path createTempFile(String prefix, String suffix) {
            throw new UnsupportedOperationException();
        }
        @Override public void delete(Path path) {}
        @Override public void close() { tfmClosed.set(true); }
    };
    var engine = new DefaultDocumentEngine(
        List.of(mock(TemplateEngine.class)), List.of(converter), resolver, validator, manager);

    engine.close();

    assertThat(converterClosed).isTrue();
    assertThat(tfmClosed).isTrue();
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `mvn -B -pl doc-engine-core test "-Dtest=DefaultDocumentEngineTest"`
Expected: COMPILATION ERROR — `method does not override or implement a method from a supertype` (у `DocumentConverter` нет `close()`).

- [ ] **Step 3: Минимальная реализация**

`DocumentConverter.java` — целиком:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;

import java.nio.file.Path;

public interface DocumentConverter extends AutoCloseable {
    boolean supports(DocumentFormat from, DocumentFormat to);
    Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx);

    /** Releases converter resources (process pools etc.); no-op by default, must be idempotent. */
    @Override
    default void close() {}
}
```

В `DefaultDocumentEngine` заменить существующий `close()`:

```java
@Override
public void close() {
    for (DocumentConverter converter : converters) {
        try {
            converter.close();
        } catch (Exception e) {
            log.warn("failed to close converter {}: {}",
                converter.getClass().getSimpleName(), e.getMessage());
        }
    }
    tempFiles.close();
}
```

- [ ] **Step 4: Тесты зелёные**

Run: `mvn -B -pl doc-engine-core test "-Dtest=DefaultDocumentEngineTest"`
Expected: `Tests run: 11, Failures: 0, Errors: 0`

- [ ] **Step 5: Полный прогон и коммит**

Run: `mvn -B verify` → BUILD SUCCESS.

```bash
git add doc-engine-core
git commit -m "feat: cascade close() from engine to document converters"
```

---

### Task 2: Модуль doc-engine-jodconverter — JodDocumentConverter на моках

**Files:**
- Modify: `pom.xml` (корневой: `<modules>`, `<properties>`, `<dependencyManagement>`)
- Create: `doc-engine-jodconverter/pom.xml`
- Create: `doc-engine-jodconverter/src/main/java/io/github/nikolaynn/docengine/jod/JodDocumentConverter.java`
- Test: `doc-engine-jodconverter/src/test/java/io/github/nikolaynn/docengine/jod/JodDocumentConverterTest.java`

**Interfaces:**
- Consumes: `DocumentConverter` (с `default close()` из Task 1), `ConvertContext(Duration timeout, TempFileManager tempFileManager, String templateHint)`, `DocumentConversionException(hint, from, to, message, cause, timeout)` и фабрика `DocumentConversionException.timeout(hint, from, to, Duration)`.
- Produces: `public final class JodDocumentConverter implements DocumentConverter` с `public JodDocumentConverter(Config)`, package-private `JodDocumentConverter(OfficeManager, Duration)`, `public synchronized void start()`, `public record Config(Path officeHome, int poolSize, Duration taskTimeout, Duration taskQueueTimeout, int maxTasksPerProcess, Path workingDir)` со статическим `Config.builder()` и `Config.defaults()`. Task 4 вызывает `new JodDocumentConverter(config)`.

- [ ] **Step 1: Подключить модуль в корневой pom**

В `pom.xml`: в `<modules>` после `doc-engine-core` добавить:

```xml
<module>doc-engine-jodconverter</module>
```

В `<properties>` добавить:

```xml
<jodconverter.version>4.4.9</jodconverter.version>
```

В `<dependencyManagement><dependencies>` добавить:

```xml
<dependency>
    <groupId>io.github.nikolaynn</groupId>
    <artifactId>doc-engine-jodconverter</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>org.jodconverter</groupId>
    <artifactId>jodconverter-local</artifactId>
    <version>${jodconverter.version}</version>
</dependency>
```

(Если управляемой записи `doc-engine-core` там нет — сверить, как её резолвит стартер, и повторить тот же приём.)

- [ ] **Step 2: pom модуля**

`doc-engine-jodconverter/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.nikolaynn</groupId>
        <artifactId>doc-generator-engine</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>doc-engine-jodconverter</artifactId>
    <name>Document Generator Engine — JODConverter Pool</name>

    <dependencies>
        <dependency>
            <groupId>io.github.nikolaynn</groupId>
            <artifactId>doc-engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jodconverter</groupId>
            <artifactId>jodconverter-local</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Написать падающие юнит-тесты**

`JodDocumentConverterTest.java` — целиком:

```java
package io.github.nikolaynn.docengine.jod;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JodDocumentConverterTest {

    @Test
    void supportsXlsxToPdfOnly() {
        var c = new JodDocumentConverter(mock(OfficeManager.class), Duration.ofSeconds(5));
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).isTrue();
        assertThat(c.supports(DocumentFormat.PDF, DocumentFormat.XLSX)).isFalse();
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.XLSX)).isFalse();
    }

    @Test
    void doesNotStartPoolOnConstruction() {
        OfficeManager manager = mock(OfficeManager.class);
        new JodDocumentConverter(manager, Duration.ofSeconds(5));
        verifyNoInteractions(manager);
    }

    @Test
    void startsPoolOnceOnFirstConversion(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        // мок-менеджер не делает работы, срабатывает защита "пустой вывод" — здесь это ок
        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class);
        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class);

        verify(manager, times(1)).start();
    }

    @Test
    void closeStopsStartedPoolAndIsIdempotent() throws Exception {
        OfficeManager manager = runningManager();
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        c.start();

        c.close();
        c.close();

        verify(manager, times(1)).stop();
    }

    @Test
    void closeWithoutStartDoesNotStopManager() throws Exception {
        OfficeManager manager = mock(OfficeManager.class);
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));

        c.close();

        verify(manager, never()).stop();
    }

    @Test
    void convertAfterCloseThrowsIllegalState(@TempDir Path tmp) throws Exception {
        var c = new JodDocumentConverter(mock(OfficeManager.class), Duration.ofSeconds(5));
        c.close();
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void officeExceptionMapsToConversionException(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        doThrow(new OfficeException("boom")).when(manager).execute(any());
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("boom")
            .matches(e -> !((DocumentConversionException) e).isTimeout());
    }

    @Test
    void timeoutCauseMapsToTimeoutConversionException(@TempDir Path tmp) throws Exception {
        OfficeManager manager = runningManager();
        doThrow(new OfficeException("task timed out", new TimeoutException("120s")))
            .when(manager).execute(any());
        var c = new JodDocumentConverter(manager, Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.xlsx"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class)
            .matches(e -> ((DocumentConversionException) e).isTimeout());
    }

    @Test
    void rejectsUnsupportedPair(@TempDir Path tmp) throws Exception {
        var c = new JodDocumentConverter(mock(OfficeManager.class), Duration.ofSeconds(5));
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        Path input = Files.writeString(tmp.resolve("in.pdf"), "stub");

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.PDF, DocumentFormat.XLSX, ctx(tfm)))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("unsupported");
    }

    /** LocalConverter отклоняет не-запущенный менеджер, поэтому стабим isRunning(). */
    private static OfficeManager runningManager() {
        OfficeManager manager = mock(OfficeManager.class);
        lenient().when(manager.isRunning()).thenReturn(true);
        return manager;
    }

    private static ConvertContext ctx(TempFileManager tfm) {
        return new ConvertContext(Duration.ofSeconds(5), tfm, "tpl");
    }
}
```

Примечание для исполнителя: если `verify`/`lenient` без MockitoExtension ругаются (`UnnecessaryStubbing` не возникает в plain-Mockito; `lenient()` статический доступен с Mockito 4) — используйте `Mockito.lenient()`. Если `LocalConverter` в рантайме требует чего-то ещё от мока (выяснится на GREEN-прогоне) — стабить минимально необходимое и зафиксировать комментарием.

- [ ] **Step 4: Убедиться, что тесты падают**

Run: `mvn -B -pl doc-engine-jodconverter test`
Expected: COMPILATION ERROR — `cannot find symbol: class JodDocumentConverter`.

- [ ] **Step 5: Реализация**

`JodDocumentConverter.java` — целиком:

```java
package io.github.nikolaynn.docengine.jod;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * XLSX->PDF converter backed by a pool of long-lived LibreOffice processes
 * managed by JODConverter: tens/hundreds of milliseconds per conversion on a
 * warm process instead of the multi-second soffice cold start.
 *
 * <p>The pool (and the underlying office manager) is created and started
 * lazily on the first conversion; call {@link #start()} to warm up eagerly.
 * {@link #close()} stops the pool and is idempotent.
 *
 * <p>{@code ConvertContext.timeout()} is NOT applied by this converter:
 * JODConverter configures the task timeout per manager, see
 * {@link Config#taskTimeout()}.
 */
public final class JodDocumentConverter implements DocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(JodDocumentConverter.class);
    private static final int BASE_PORT = 2002;

    private final Config config;          // null когда менеджер внедрён напрямую (тесты)
    private final Duration taskTimeout;   // для сообщения об ошибке таймаута
    private OfficeManager officeManager;  // создаётся лениво в start()
    private boolean started;
    private boolean closed;

    public JodDocumentConverter(Config config) {
        this.config = config == null ? Config.defaults() : config;
        this.taskTimeout = this.config.taskTimeout();
        this.officeManager = null;
    }

    JodDocumentConverter(OfficeManager officeManager, Duration taskTimeout) {
        this.config = null;
        this.taskTimeout = taskTimeout;
        this.officeManager = officeManager;
    }

    /** Pool configuration; all fields optional, see builder defaults. */
    public record Config(Path officeHome,
                         int poolSize,
                         Duration taskTimeout,
                         Duration taskQueueTimeout,
                         int maxTasksPerProcess,
                         Path workingDir) {
        public Config {
            if (poolSize < 1) throw new IllegalArgumentException("poolSize must be >= 1");
            if (maxTasksPerProcess < 1) throw new IllegalArgumentException("maxTasksPerProcess must be >= 1");
            taskTimeout = taskTimeout == null ? Duration.ofSeconds(120) : taskTimeout;
            taskQueueTimeout = taskQueueTimeout == null ? Duration.ofSeconds(30) : taskQueueTimeout;
        }

        public static Builder builder() { return new Builder(); }

        public static Config defaults() { return builder().build(); }

        public static final class Builder {
            private Path officeHome;
            private int poolSize = 1;
            private Duration taskTimeout = Duration.ofSeconds(120);
            private Duration taskQueueTimeout = Duration.ofSeconds(30);
            private int maxTasksPerProcess = 200;
            private Path workingDir;

            public Builder officeHome(Path v) { this.officeHome = v; return this; }
            public Builder poolSize(int v) { this.poolSize = v; return this; }
            public Builder taskTimeout(Duration v) { this.taskTimeout = v; return this; }
            public Builder taskQueueTimeout(Duration v) { this.taskQueueTimeout = v; return this; }
            public Builder maxTasksPerProcess(int v) { this.maxTasksPerProcess = v; return this; }
            public Builder workingDir(Path v) { this.workingDir = v; return this; }

            public Config build() {
                return new Config(officeHome, poolSize, taskTimeout, taskQueueTimeout,
                    maxTasksPerProcess, workingDir);
            }
        }
    }

    /**
     * Starts the LibreOffice pool eagerly (otherwise it starts on the first
     * conversion). Idempotent while the converter is open.
     */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("converter is closed");
        }
        if (started) {
            return;
        }
        if (officeManager == null) {
            // построение отложено сюда: LocalOfficeManager.builder() валидирует
            // officeHome и падает на машинах без установленного LibreOffice
            officeManager = buildManager(config);
        }
        try {
            officeManager.start();
            started = true;
        } catch (OfficeException e) {
            throw new DocumentConversionException(null, DocumentFormat.XLSX, DocumentFormat.PDF,
                "failed to start LibreOffice pool: " + e.getMessage(), e, false);
        }
    }

    @Override
    public boolean supports(DocumentFormat from, DocumentFormat to) {
        return from == DocumentFormat.XLSX && to == DocumentFormat.PDF;
    }

    @Override
    public Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx) {
        if (!supports(from, to)) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "unsupported conversion " + from + "->" + to, null, false);
        }
        start();

        Path managed = ctx.tempFileManager().createTempFile("doc-engine-pdf-", "." + to.extension());
        try {
            LocalConverter.builder().officeManager(officeManager).build()
                .convert(input.toFile())
                .to(managed.toFile())
                .execute();
            if (Files.size(managed) == 0) {
                throw new DocumentConversionException(ctx.templateHint(), from, to,
                    "LibreOffice produced no output", null, false);
            }
            log.debug("converted {} -> {}", input, managed);
            return managed;
        } catch (OfficeException e) {
            ctx.tempFileManager().delete(managed);
            if (hasTimeoutCause(e)) {
                throw DocumentConversionException.timeout(ctx.templateHint(), from, to, taskTimeout);
            }
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "LibreOffice conversion failed: " + e.getMessage(), e, false);
        } catch (IOException e) {
            ctx.tempFileManager().delete(managed);
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "failed to read conversion output", e, false);
        } catch (DocumentConversionException e) {
            ctx.tempFileManager().delete(managed);
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (started) {
            try {
                officeManager.stop();
            } catch (OfficeException e) {
                log.warn("failed to stop LibreOffice pool: {}", e.getMessage());
            }
        }
    }

    private static OfficeManager buildManager(Config config) {
        LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
            .portNumbers(IntStream.range(BASE_PORT, BASE_PORT + config.poolSize()).toArray())
            .taskExecutionTimeout(config.taskTimeout().toMillis())
            .taskQueueTimeout(config.taskQueueTimeout().toMillis())
            .maxTasksPerProcess(config.maxTasksPerProcess());
        if (config.officeHome() != null) {
            builder.officeHome(config.officeHome().toFile());
        }
        if (config.workingDir() != null) {
            builder.workingDir(config.workingDir().toFile());
        }
        return builder.build();
    }

    private static boolean hasTimeoutCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
```

Примечание: порядок catch-блоков важен — `DocumentConversionException` (unchecked) ловится последним, чтобы «пустой вывод» тоже удалял managed-файл. Если сигнатуры JODConverter 4.4.9 отличаются от использованных (имена методов builder'а, чек `isRunning`), поправить по факту компиляции и зафиксировать в коммит-сообщении.

- [ ] **Step 6: Тесты зелёные**

Run: `mvn -B -pl doc-engine-jodconverter test` (при необходимости сначала `mvn -B -pl doc-engine-core install "-Dmaven.test.skip=true"` либо собрать реактор целиком: `mvn -B test`)
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 7: Полный прогон и коммит**

Run: `mvn -B verify` → BUILD SUCCESS (новый модуль в реакторе, jacoco/failsafe наследуются).

```bash
git add pom.xml doc-engine-jodconverter
git commit -m "feat: doc-engine-jodconverter module with pooled LibreOffice converter"
```

---

### Task 3: Интеграционный тест реального пула (гейт soffice)

**Files:**
- Create: `doc-engine-jodconverter/src/test/java/io/github/nikolaynn/docengine/jod/JodDocumentConverterIT.java`

**Interfaces:**
- Consumes: `JodDocumentConverter(Config)`, `Config.builder().workingDir(...)`, `DefaultTempFileManager`, `ConvertContext` (из Task 2 и core).
- Produces: ничего для других задач; выполняется в CI-джобе `libreoffice-it`.

- [ ] **Step 1: Написать IT (падать локально не обязан — он гейтится)**

`JodDocumentConverterIT.java` — целиком:

```java
package io.github.nikolaynn.docengine.jod;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("sofficeAvailable")
class JodDocumentConverterIT {

    static boolean sofficeAvailable() {
        try {
            Process p = new ProcessBuilder("soffice", "--version")
                .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    void convertsXlsxToPdfOnPooledProcess(@TempDir Path tmp) throws Exception {
        try (JodDocumentConverter converter = new JodDocumentConverter(
                JodDocumentConverter.Config.builder().workingDir(tmp).build())) {
            TempFileManager tfm = new DefaultTempFileManager(tmp, false);
            Path xlsx = writeSampleWorkbook(tmp.resolve("in.xlsx"));

            Path pdf = converter.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(60), tfm, "it"));

            assertThat(pdf).exists();
            assertThat(Files.size(pdf)).isGreaterThan(100);
        }
    }

    @Test
    void handlesFourConcurrentConversions(@TempDir Path tmp) throws Exception {
        try (JodDocumentConverter converter = new JodDocumentConverter(
                JodDocumentConverter.Config.builder().workingDir(tmp).build())) {
            TempFileManager tfm = new DefaultTempFileManager(tmp, false);
            List<Callable<Path>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Path xlsx = writeSampleWorkbook(tmp.resolve("in-" + i + ".xlsx"));
                jobs.add(() -> converter.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
                    new ConvertContext(Duration.ofSeconds(60), tfm, "it")));
            }

            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                for (Future<Path> f : pool.invokeAll(jobs)) {
                    assertThat(Files.size(f.get())).isGreaterThan(100);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static Path writeSampleWorkbook(Path target) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sh = wb.createSheet("data");
            sh.createRow(0).createCell(0).setCellValue("hello from jod pool");
            try (var out = Files.newOutputStream(target)) {
                wb.write(out);
            }
        }
        return target;
    }
}
```

(POI приходит транзитивно из doc-engine-core. try-with-resources законен: `DocumentConverter extends AutoCloseable`.)

- [ ] **Step 2: Локальная проверка (скип) и компиляция**

Run: `mvn -B -pl doc-engine-jodconverter verify`
Expected: `JodDocumentConverterIT ... Tests run: 2, Skipped: 2` (локально soffice нет), BUILD SUCCESS.

- [ ] **Step 3: Коммит**

```bash
git add doc-engine-jodconverter/src/test
git commit -m "test: gated integration tests for pooled LibreOffice conversion"
```

Реальную проверку IT даст CI-джоб `libreoffice-it` после пуша (LibreOffice установлен → гейт истинен → пул реально стартует).

---

### Task 4: Автоактивация в стартере

**Files:**
- Modify: `doc-engine-spring-boot-starter/pom.xml` (optional-зависимость на модуль)
- Modify: `doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/DocEngineProperties.java` (вложенный record `Jod`)
- Create: `doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/JodConverterAutoConfiguration.java`
- Modify: `doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/DocEngineAutoConfiguration.java` (условие `libreOfficeConverter`)
- Modify: `doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories`
- Modify: `doc-engine-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `doc-engine-spring-boot-starter/src/test/java/io/github/nikolaynn/docengine/starter/DocEngineAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `JodDocumentConverter(Config)` и `Config.builder()` из Task 2.
- Produces: бин `jodDocumentConverter` (тип `DocumentConverter`); свойства `doc-engine.converter.jod.*`; изменённая семантика дефолта `libreOfficeConverter` (`@ConditionalOnMissingBean(DocumentConverter.class)`).

- [ ] **Step 1: Написать падающие тесты**

В `DocEngineAutoConfigurationTest`:

1. Расширить runner (заменить существующее поле):

```java
private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(
        JodConverterAutoConfiguration.class, DocEngineAutoConfiguration.class));
```

2. Обновить `defaultsAllBeansPresent`: заменить строку `assertThat(ctx).hasBean("libreOfficeConverter");` на

```java
assertThat(ctx).hasBean("jodDocumentConverter");
assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
```

3. Заменить тест `libreOfficeConverterCanBeDisabledViaProperty` на:

```java
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
```

4. Обновить `userConverterReplacesDefault` (семантика теперь типовая, имя бина неважно):

```java
@Test
void userConverterSuppressesAllDefaultConverters() {
    runner.withUserConfiguration(UserConverterConfig.class).run(ctx -> {
        assertThat(ctx.getBeansOfType(DocumentConverter.class)).hasSize(1);
        assertThat(ctx).doesNotHaveBean("jodDocumentConverter");
        assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
    });
}
```

и в `UserConverterConfig` убрать привязку к имени:

```java
@Configuration(proxyBeanMethods = false)
static class UserConverterConfig {
    @Bean
    DocumentConverter userConverter() { return mock(DocumentConverter.class); }
}
```

5. Добавить биндинг свойств:

```java
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
```

- [ ] **Step 2: Убедиться, что тесты падают**

Сначала в `doc-engine-spring-boot-starter/pom.xml` добавить зависимость (иначе тесты не скомпилируются по другой причине):

```xml
<dependency>
    <groupId>io.github.nikolaynn</groupId>
    <artifactId>doc-engine-jodconverter</artifactId>
    <optional>true</optional>
</dependency>
```

Run: `mvn -B test -pl doc-engine-spring-boot-starter -am "-Dtest=DocEngineAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: COMPILATION ERROR — `cannot find symbol: class JodConverterAutoConfiguration` / `method jod()`.

- [ ] **Step 3: Реализация**

`DocEngineProperties.java` — целиком:

```java
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
```

`JodConverterAutoConfiguration.java` — целиком:

```java
package io.github.nikolaynn.docengine.starter;

import io.github.nikolaynn.docengine.jod.JodDocumentConverter;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Активируется, когда doc-engine-jodconverter есть на classpath: пул
 * LibreOffice-процессов становится основным конвертером, процессный
 * конвертер из DocEngineAutoConfiguration отступает (его условие —
 * отсутствие других DocumentConverter-бинов).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JodDocumentConverter.class)
@AutoConfigureBefore(DocEngineAutoConfiguration.class)
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
```

В `DocEngineAutoConfiguration` заменить условие бина `libreOfficeConverter`:

```java
@Bean(name = "libreOfficeConverter")
@ConditionalOnMissingBean(DocumentConverter.class)
@ConditionalOnProperty(prefix = "doc-engine.converter.libreoffice",
                       name = "enabled", havingValue = "true", matchIfMissing = true)
public DocumentConverter libreOfficeConverter(DocEngineProperties props) {
    var lo = props.converter().libreoffice();
    return new LibreOfficeConverter(lo.executable(), lo.timeout(), lo.workingDir());
}
```

`spring.factories` — целиком:

```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
io.github.nikolaynn.docengine.starter.JodConverterAutoConfiguration,\
io.github.nikolaynn.docengine.starter.DocEngineAutoConfiguration
```

`AutoConfiguration.imports` — целиком:

```
io.github.nikolaynn.docengine.starter.JodConverterAutoConfiguration
io.github.nikolaynn.docengine.starter.DocEngineAutoConfiguration
```

Тест `registeredViaAutoConfigurationImportsForBoot3` дополнить проверкой:

```java
assertThat(candidates).contains(JodConverterAutoConfiguration.class.getName());
```

- [ ] **Step 4: Тесты зелёные**

Run: `mvn -B test -pl doc-engine-spring-boot-starter -am`
Expected: все тесты стартера зелёные (11 шт.), включая новые.

- [ ] **Step 5: Полный прогон и коммит**

Run: `mvn -B verify` → BUILD SUCCESS.

```bash
git add doc-engine-spring-boot-starter
git commit -m "feat: auto-configure pooled JODConverter when module is on classpath"
```

---

### Task 5: README и финальная верификация

**Files:**
- Modify: `README.md` (таблица модулей, секция про пул, yaml-пример)

**Interfaces:**
- Consumes: всё из Task 2–4.
- Produces: документацию; ничего кодового.

- [ ] **Step 1: Обновить README**

1. В таблицу модулей добавить строку:

```markdown
| `doc-engine-jodconverter` | Быстрая PDF-конверсия: пул долгоживущих LibreOffice-процессов (JODConverter). Опциональный модуль. |
```

2. После секции «Быстрый старт — plain Java» добавить секцию:

```markdown
## Быстрая конверсия PDF: пул LibreOffice

По умолчанию конвертация в PDF запускает новый процесс `soffice` на каждый
документ (cold start 2–6 секунд). Для постоянного потока конверсий подключите
модуль пула:

```xml
<dependency>
    <groupId>io.github.nikolaynn</groupId>
    <artifactId>doc-engine-jodconverter</artifactId>
    <version>0.1.0</version>
</dependency>
```

В Spring Boot этого достаточно: конвертер пула автоматически становится
основным, процессный отключается (откат: `doc-engine.converter.jod.enabled: false`).
Пул стартует лениво при первой конверсии и останавливается при закрытии
контекста.

Plain Java:

```java
JodDocumentConverter jod = new JodDocumentConverter(
    JodDocumentConverter.Config.builder().poolSize(2).build());
jod.start(); // необязательный прогрев; иначе пул стартует при первой конверсии

DocumentEngine engine = DocumentEngineBuilder.create()
    .withJxlsEngine()
    .addConverter(jod)
    .withDefaultTempFileManager(null, true)
    .build();
// engine.close() остановит пул
```

Свойства стартера (`doc-engine.converter.jod.*`): `enabled` (true),
`office-home` (автодетект), `pool-size` (1), `task-timeout` (120s),
`task-queue-timeout` (30s), `max-tasks-per-process` (200).
Примечание: `GenerationOptions.timeout` этим конвертером не применяется —
таймаут задаётся на уровне пула.
```

3. В yaml-пример конфигурации добавить блок:

```yaml
    jod:                                 # если подключён doc-engine-jodconverter
      enabled: true
      pool-size: 2
      task-timeout: 120s
```

- [ ] **Step 2: Финальная верификация**

Run: `mvn -B verify`
Expected: BUILD SUCCESS, все модули зелёные, «All coverage checks have been met».

- [ ] **Step 3: Коммит**

```bash
git add README.md
git commit -m "docs: document the JODConverter pool module"
```

После завершения плана: пуш и контроль CI-джоба `libreoffice-it` (он впервые реально прогонит `JodDocumentConverterIT`) — по команде пользователя.
