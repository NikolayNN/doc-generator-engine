# Document Generator Engine — Design

**Date:** 2026-05-26
**Status:** Draft, pending approval

## 1. Цель и границы

Java-библиотека, предоставляющая основному приложению единый стабильный API для генерации пользовательских документов по офисным шаблонам. Приложение готовит данные, выбирает шаблон, вызывает библиотеку — и получает готовый документ (имя, MIME, байты).

Библиотека выступает как абстрактный *document engine*: скрывает выбор шаблонизатора, обработку офисного файла, работу с временными файлами и конвертацию в PDF.

### Разделение ответственности

**Основное приложение:** бизнес-данные, права, хранение/выбор шаблонов, доставка результата пользователю.

**Document engine:** получает шаблон, применяет данные, формирует промежуточный офисный документ, опционально конвертирует в PDF, возвращает результат, инкапсулирует внутренние технологии.

### Не делаем в MVP

- собственный язык шаблонов или редактор;
- асинхронный API;
- поддержка форматов кроме XLSX и PDF;
- POJO-binding для входных данных;
- TemplateResolver сложнее identity (БД/S3/HTTP source — на стороне приложения);
- валидация токенов на этапе registration шаблона;
- ретраи, метрики, трассировка (это политика приложения и/или декораторы поверх).

## 2. MVP — фиксированные решения

| Аспект | Решение |
|---|---|
| Формат шаблона | XLSX |
| Форматы вывода | XLSX (passthrough) и PDF |
| Шаблонизатор XLSX | JXLS 2.x (поверх Apache POI) |
| Конвертация в PDF | LibreOffice headless (`soffice --headless --convert-to pdf`) |
| Среда выполнения | Spring Boot 2.7 (с прицелом на 3.x), Java 17+ |
| Упаковка | Два модуля: `doc-engine-core` (plain Java) + `doc-engine-spring-boot-starter` |
| Модель данных | `Map<String, Object>` |
| Источник шаблона | `InputStream` / `byte[]` (приложение само достаёт) |
| API | Синхронный блокирующий |
| Ошибки | Иерархия unchecked `DocumentGenerationException` |

## 3. Архитектура

Вариант «тонкая обёртка, два контракта»: оркестратор `DocumentEngine` поверх двух главных SPI — `TemplateEngine` (шаблон + данные → промежуточный документ) и `DocumentConverter` (документ → документ другого формата).

### 3.1 Структура проекта

```
doc-generator-engine/                    ← parent pom
├── doc-engine-core/                     ← plain Java, без Spring
│   └── src/main/java/.../docengine/
│       ├── api/                         ← public API
│       │   ├── DocumentEngine           ← фасад
│       │   ├── GenerationRequest        ← record
│       │   ├── GenerationResult         ← record
│       │   ├── GenerationOptions        ← record
│       │   ├── TemplateReference        ← sealed interface
│       │   ├── DocumentFormat           ← enum (XLSX, PDF)
│       │   └── exception/               ← иерархия исключений
│       ├── spi/                         ← extension points
│       │   ├── TemplateEngine
│       │   ├── DocumentConverter
│       │   ├── TemplateResolver
│       │   ├── TemplateValidator
│       │   └── TempFileManager
│       └── internal/                    ← реализации, package-private
│           ├── DefaultDocumentEngine
│           ├── jxls/JxlsTemplateEngine
│           ├── libreoffice/LibreOfficeConverter
│           ├── resolver/InputStreamTemplateResolver
│           └── tempfile/DefaultTempFileManager
└── doc-engine-spring-boot-starter/      ← тонкая обёртка
    └── src/main/java/.../starter/
        ├── DocEngineAutoConfiguration
        ├── DocEngineProperties          ← @ConfigurationProperties("doc-engine")
        └── META-INF/spring.factories
```

`internal/` package-private — наружу торчат только интерфейсы и публичные record'ы. Расширение через SPI-бины (Spring) или builder (plain Java).

### 3.2 Зависимости core

- `org.jxls:jxls` (2.x)
- `org.apache.commons:commons-jexl3` (нужен JXLS)
- Apache POI (транзитивно через JXLS)
- SLF4J api (без реализации)

Конкретные версии JXLS/POI/JEXL фиксируются на этапе implementation plan; здесь — только мажорные ветки.

LibreOffice — не Java-зависимость, ожидается в окружении (`soffice` в PATH или явный путь через конфиг).

### 3.3 Базовый Java-пакет

В этом документе используется конвенциональный плейсхолдер `io.github.nikolaynn.docengine` / `io.github.nikolaynn.docengine.starter`. Окончательное имя базового пакета выбирается на этапе implementation plan (предположительно по namespace owning-проекта).

## 4. Контракты

### 4.1 Public API

```java
public interface DocumentEngine {
    GenerationResult generate(GenerationRequest request);
}

public record GenerationRequest(
    TemplateReference template,
    Map<String, Object> data,
    DocumentFormat targetFormat,         // XLSX или PDF
    GenerationOptions options            // null = defaults()
) {}

public record GenerationResult(
    String fileName,                     // "report-2026-05-26.pdf"
    String mimeType,                     // "application/pdf"
    DocumentFormat format,
    byte[] content
) {}

public record GenerationOptions(
    String fileNameHint,                 // null = из шаблона / дефолт
    Duration timeout,                    // null = no timeout
    Locale locale,                       // null = system default
    Map<String, Object> engineHints      // open map для движков (например sheetName)
) {
    public static GenerationOptions defaults() { ... }
}

public sealed interface TemplateReference
        permits TemplateReference.InputStreamRef,
                TemplateReference.BytesRef {
    DocumentFormat sourceFormat();
    String hint();                       // имя/идентификатор для логов и ошибок

    record InputStreamRef(InputStream stream, DocumentFormat sourceFormat, String hint)
            implements TemplateReference {}
    record BytesRef(byte[] bytes, DocumentFormat sourceFormat, String hint)
            implements TemplateReference {}
}

public enum DocumentFormat {
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");
    public String mimeType() { ... }
    public String extension() { ... }
}
```

**Эволюция API.** Если в будущем понадобится POJO-binding, добавится перегрузка `generate(...)` с `Object data` и новый SPI `DataBinding`. Текущая сигнатура с `Map<String, Object>` — намеренный осознанный выбор: строгий тип сейчас, явная миграция позже.

### 4.2 SPI

```java
public interface TemplateEngine {
    boolean supports(DocumentFormat sourceFormat);
    /** Рендерит шаблон с данными в файл того же формата, что и source. */
    Path render(TemplateReference template, Map<String, Object> data, RenderContext ctx);
}

public interface DocumentConverter {
    boolean supports(DocumentFormat from, DocumentFormat to);
    /** Конвертирует входной файл в target-формат, возвращает новый файл. */
    Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx);
}

public interface TemplateResolver {
    /** Превращает TemplateReference в локальный ResolvedTemplate (Path или bytes). */
    ResolvedTemplate resolve(TemplateReference ref);
}

public interface TemplateValidator {
    /** В MVP — no-op. Зарезервирован под валидацию токенов в v2. */
    void validate(TemplateReference ref);
}

public interface TempFileManager {
    Path createTempFile(String prefix, String suffix);
    void delete(Path path);
}
```

Контекстные объекты (`RenderContext`, `ConvertContext`) несут `locale`, `timeout`, `engineHints`, `TempFileManager`. Это даёт SPI всё необходимое, не привязывая их к `GenerationRequest`.

## 5. Поток данных

```
                  DefaultDocumentEngine.generate(request)
                                |
                  1. validate request (non-null поля, format поддержан)
                                |
                  2. TemplateValidator.validate(template)   ← no-op в MVP
                                |
                  3. TemplateResolver.resolve(template)     → ResolvedTemplate
                                |
                  4. TemplateEngine.render(resolved, data, ctx)
                                |                            (выбор по supports(sourceFormat))
                                |                            → renderedFile : Path (XLSX)
                                |
                  5. targetFormat == sourceFormat ?
                       ├── да  → readAllBytes(renderedFile)
                       └── нет → DocumentConverter.convert(renderedFile, XLSX, PDF, ctx)
                                                  (выбор по supports(from, to))
                                                  → convertedFile : Path
                                |
                  6. собрать GenerationResult(fileName, mimeType, format, bytes)
                                |
                  7. finally: TempFileManager.delete(renderedFile, convertedFile)
                                |
                                ▼
                        return GenerationResult
```

### 5.1 Выбор SPI-реализаций

Registry pattern. `List<TemplateEngine>` и `List<DocumentConverter>` инжектятся в `DefaultDocumentEngine`. Выбор — первый, у которого `supports(...) == true`. Отсутствие подходящей реализации — `UnsupportedTemplateFormatException` / `UnsupportedConversionException`.

В Spring-сценарии списки собираются автоматически. В plain-Java — через `DocumentEngineBuilder`:

```java
DocumentEngine engine = DocumentEngineBuilder.create()
    .addTemplateEngine(new JxlsTemplateEngine())
    .addConverter(new LibreOfficeConverter(Path.of("/usr/bin/soffice"), Duration.ofSeconds(60), null))
    .tempFileManager(new DefaultTempFileManager(Path.of("/tmp/docengine"), true))
    .build();
```

### 5.2 Сборка имени файла

1. Если задан `options.fileNameHint()` — он + расширение из `targetFormat`.
2. Иначе `template.hint()` + `-<timestamp>` + расширение.
3. Если и `hint` пустой — `document-<timestamp>.<ext>`.

`mimeType` всегда берётся из `DocumentFormat.mimeType()` и гарантированно согласован с `format`.

### 5.3 Реализации SPI в MVP

| SPI | Реализация | Поведение |
|---|---|---|
| `TemplateResolver` | `InputStreamTemplateResolver` | `BytesRef` → байты как есть; `InputStreamRef` → читает в byte[] |
| `TemplateValidator` | `NoopTemplateValidator` | заглушка |
| `TemplateEngine` (XLSX) | `JxlsTemplateEngine` | `JxlsHelper.processTemplate(in, out, context)` + явный `FormulaEvaluator.evaluateAll()` + `setForceFormulaRecalculation(true)` |
| `DocumentConverter` (XLSX→PDF) | `LibreOfficeConverter` | `ProcessBuilder` запускает `soffice --headless --convert-to pdf --outdir <tmp> <input>`, ждёт с таймаутом, читает результат |
| `TempFileManager` | `DefaultTempFileManager` | `Files.createTempFile` в указанной директории; cleanup на shutdown |

### 5.4 Поддержка табличных секций и формул

`JxlsTemplateEngine` через JXLS поддерживает «из коробки»:

- **Циклы по строкам.** Комментарий `jx:each(items="items", var="item", lastCell="D2")` в первой ячейке строки-образца — строка раскрывается для каждого элемента `List`, итоги ниже сдвигаются, стили сохраняются.
- **Вложенные циклы** — `jx:each` внутри `jx:each`.
- **Условные блоки** — `jx:if(...)`.
- **Группировка** — `jx:each(... groupBy="...")`.
- **Множественные секции и листы**, в т.ч. `multisheet`.
- **Формулы.** Формулы шаблона (например `=A1+A2`, `=SUM(D2:D2)`) остаются формулами; ссылки на расширяющиеся диапазоны JXLS сдвигает корректно. После рендера `JxlsTemplateEngine` вызывает `FormulaEvaluator.evaluateAll()` — в XLSX лежат и формулы, и cached values, поэтому любой потребитель (Excel, LibreOffice, POI-читатель) видит правильный результат. PDF-конвертация через LibreOffice также пересчитывает формулы.

### 5.5 Threading

- `DefaultDocumentEngine` stateless и thread-safe (все зависимости immutable, mutable state — только локальные temp-файлы).
- `LibreOfficeConverter` запускает **отдельный процесс `soffice` на каждый вызов**. Это медленнее пула, но безопаснее: зависший вызов не блокирует остальные. Пул `soffice`-процессов (через `UnoServer`/JODConverter) — потенциальная альтернативная реализация позже.
- Таймаут: `process.waitFor(timeout)`; при превышении — `process.destroyForcibly()` и `DocumentConversionException` с пометкой `timeout=true`.

## 6. Модель ошибок

Иерархия unchecked-исключений в `api/exception/`:

```
RuntimeException
   └── DocumentGenerationException                ← корень, не бросается напрямую
         ├── InvalidGenerationRequestException    ← null-поля, неподдерживаемый формат
         ├── TemplateResolutionException          ← не смогли прочитать шаблон
         ├── TemplateValidationException          ← валидатор отверг (v2)
         ├── TemplateRenderingException           ← JXLS/JEXL упал на подстановке
         ├── UnsupportedTemplateFormatException   ← нет TemplateEngine для формата
         ├── UnsupportedConversionException       ← нет DocumentConverter для пары
         ├── DocumentConversionException          ← soffice упал/таймаут/ненулевой exit
         └── TempFileException                    ← I/O для temp-файлов
```

Контракт корневого исключения:

```java
public class DocumentGenerationException extends RuntimeException {
    private final String templateHint;
    private final DocumentFormat sourceFormat;
    private final DocumentFormat targetFormat;
    // getters; toString включает все три поля
}
```

### 6.1 Маппинг внутренних исключений

| Источник | Публичное исключение | Cause |
|---|---|---|
| `JxlsException`, `JexlException` | `TemplateRenderingException` | оригинал |
| `IOException` при чтении шаблона | `TemplateResolutionException` | оригинал |
| `soffice` exit != 0 | `DocumentConversionException` | сообщение со stderr (truncated) |
| `soffice` timeout | `DocumentConversionException` (`timeout=true`) | — |
| `IOException` при temp-файлах | `TempFileException` | оригинал |

### 6.2 Принципы

- Внутренние библиотечные исключения (JXLS, POI, IO) **никогда** не утекают наружу — всегда оборачиваются.
- Сообщения исключений включают `templateHint`, `targetFormat`, фрагмент stderr — но **не** данные подстановки и **не** содержимое шаблона (PII).
- Логирование через SLF4J: `DEBUG` для рендеринга/конверсии/удаления temp, `WARN` только при cleanup-фейлах после успешной генерации. Стектрейсы пользовательских ошибок — задача приложения.
- `InvalidGenerationRequestException` — fail-fast на входе `generate()`, до любых I/O.

## 7. Spring Boot starter (для Boot 2.7, совместим с 3.x)

### 7.1 Конфигурация

```yaml
doc-engine:
  temp-dir: /var/tmp/doc-engine          # null = system temp
  cleanup-on-shutdown: true
  converter:
    libreoffice:
      enabled: true
      executable: /usr/bin/soffice       # null = искать в PATH
      timeout: 60s
      working-dir: ${doc-engine.temp-dir}
```

```java
@ConfigurationProperties("doc-engine")
public record DocEngineProperties(
    @Nullable Path tempDir,
    boolean cleanupOnShutdown,
    LibreOffice converter
) {
    public record LibreOffice(
        boolean enabled,
        @Nullable Path executable,
        Duration timeout,
        @Nullable Path workingDir
    ) {}
}
```

### 7.2 AutoConfiguration

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocEngineProperties.class)
public class DocEngineAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    public TempFileManager tempFileManager(DocEngineProperties p) { ... }

    @Bean @ConditionalOnMissingBean
    public TemplateResolver templateResolver(TempFileManager tfm) { ... }

    @Bean @ConditionalOnMissingBean
    public TemplateValidator templateValidator() { return new NoopTemplateValidator(); }

    @Bean @ConditionalOnMissingBean(name = "jxlsTemplateEngine")
    public TemplateEngine jxlsTemplateEngine() { return new JxlsTemplateEngine(); }

    @Bean @ConditionalOnMissingBean(name = "libreOfficeConverter")
    @ConditionalOnProperty(prefix = "doc-engine.converter.libreoffice",
                           name = "enabled", havingValue = "true", matchIfMissing = true)
    public DocumentConverter libreOfficeConverter(DocEngineProperties p) { ... }

    @Bean @ConditionalOnMissingBean
    public DocumentEngine documentEngine(
            List<TemplateEngine> engines,
            List<DocumentConverter> converters,
            TemplateResolver resolver,
            TemplateValidator validator,
            TempFileManager tempFiles) {
        return new DefaultDocumentEngine(engines, converters, resolver, validator, tempFiles);
    }
}
```

Регистрация — `META-INF/spring.factories` (формат Boot 2.x; работает и в 3.x):

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
io.github.nikolaynn.docengine.starter.DocEngineAutoConfiguration
```

### 7.3 Зависимости стартера

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>doc-engine-core</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

`scope=provided` для autoconfigure — стартер не навязывает версию Boot.

### 7.4 Расширение пользователем

- свой `TemplateEngine` (например, DOCX) — `@Bean`, autoconfig подхватит в `List<TemplateEngine>`;
- свой `DocumentConverter` — `@Bean` с именем `libreOfficeConverter` сдаст место по `@ConditionalOnMissingBean(name = ...)`, или дефолт отключается через `doc-engine.converter.libreoffice.enabled=false`;
- свой `TempFileManager`/`TemplateResolver`/`TemplateValidator` — `@Bean`, дефолт уйдёт по `@ConditionalOnMissingBean`.

### 7.5 Health-check (опционально)

Если `LibreOfficeConverter` включён, на старте лениво проверять, что `soffice` существует и запускается. Если нет — `WARN` в лог, но **не падать**. Пользователь может намеренно генерировать только XLSX, тогда soffice не нужен.

## 8. Стратегия тестирования

### 8.1 Юнит-тесты `doc-engine-core` (обязательны)

Без `soffice`, без сети. Mocks/stubs для SPI.

- `DefaultDocumentEngine` — оркестрация: выбор SPI, пропуск конверсии при `from == to`, cleanup в happy и error paths, сборка `fileName`/`mimeType`, маппинг внутренних исключений в публичные.
- `JxlsTemplateEngine` (реальный JXLS): простая подстановка, `jx:each`, сохранение стилей, формулы (`=A1+A2`, `=SUM(D2:D2)`), `TemplateRenderingException` при кривом выражении.
- `DefaultTempFileManager` — создание, удаление, cleanup-on-shutdown.
- `InvalidGenerationRequestException` — fail-fast на null-полях.

Тестовые шаблоны — в `src/test/resources/templates/`: `simple-fields.xlsx`, `table-each.xlsx`, `formulas.xlsx`.

### 8.2 Интеграционный тест конвертера (опциональный)

Профиль `it-libreoffice`, отдельный CI-job с установленным LibreOffice.

```java
@EnabledIf("sofficeAvailable")
class LibreOfficeConverterIT {
    @Test void convertsXlsxToPdf() { ... }
    @Test void timeoutDestroysProcess() { ... }
    @Test void nonZeroExitMapsToConversionException() { ... }
}
```

`sofficeAvailable` — проверка через `which soffice` / `where soffice.exe`. Локально без LibreOffice — `@Disabled` с причиной, билд не падает.

### 8.3 Тесты стартера

`ApplicationContextRunner` без поднятия полного контекста:

- бины по умолчанию создаются;
- `@ConditionalOnMissingBean` корректно отдаёт место пользовательскому бину;
- `@ConditionalOnProperty` для LibreOffice работает;
- `DocEngineProperties` биндит свойства.

### 8.4 Сквозное

- Никаких сетевых тестов в MVP.
- Тестовые XLSX-шаблоны коммитятся в репозиторий.
- AssertJ для ассертов, Mockito без BDD-обёрток.
- Целевое покрытие core: ≥80% по веткам как индикатор пропущенных error paths (особенно cleanup-в-finally).

### 8.5 Что не тестируем

- Внутреннее поведение JXLS и Apache POI.
- Эстетическое качество PDF, генерируемого LibreOffice.
- Производительность (отдельный бенч-проект, если понадобится).

## 9. Что закладывается под будущее (вне MVP)

- **`DataBinding` SPI** для POJO → перегрузка `generate(...)` с `Object data`.
- **DOCX**: новый `TemplateEngine` (docx-stamper или docx4j) — встанет в существующий registry без изменения API.
- **Альтернативные PDF-конвертеры**: HTTP-сервис (Gotenberg), Aspose, JODConverter с пулом — новые реализации `DocumentConverter`.
- **`TemplateResolver` для classpath/URL/S3** — дополнительные реализации SPI.
- **`TemplateValidator`** — валидация токенов (синтаксис `${...}`, неизвестные ключи) на этапе registration шаблона.
- **Версионирование шаблонов** — `TemplateReference` с `version` полем + плагин-резолвер.
- **Метрики/трассировка** — декоратор `MeteredDocumentEngine` поверх `DefaultDocumentEngine`.
- **Переход на Spring Boot 3.x** — поменяется только один файл регистрации (`spring.factories` → `META-INF/spring/...imports`).

## 10. Принятые решения и обоснования

| Решение | Обоснование |
|---|---|
| Два модуля (core + starter) | core можно использовать без Spring; starter — тонкий слой |
| JXLS, а не POI напрямую | существующий зрелый шаблонизатор; цель — обёртка над инструментами, не свой engine |
| LibreOffice headless для PDF | бесплатно, качественно, никаких лицензий; цена — внешний бинарь |
| Один процесс soffice на вызов в MVP | простота и изоляция важнее throughput; пул — позже |
| Strong-type `Map<String, Object>` | честный контракт сейчас; POJO-binding — отдельный шаг с явной перегрузкой |
| Unchecked exception-иерархия | стандарт enterprise-Java; checked-исключения избыточны для library boundary |
| Синхронный API | генерация изначально blocking; async — задача вызывающей стороны |
| Источник шаблона только bytes/stream | минимальный TemplateResolver в MVP; classpath/URL/S3 — расширение |
| TemplateValidator как no-op SPI | абстракция заложена, реализация — в v2 |
| FormulaEvaluator.evaluateAll() после рендера | гарантирует cached values в XLSX, не только в PDF |
