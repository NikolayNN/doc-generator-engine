# Document Generator Engine

Java-библиотека для генерации документов по офисным шаблонам. Приложение готовит данные и выбирает шаблон, библиотека возвращает готовый документ (имя, MIME, байты).

В MVP поддерживается рендеринг XLSX-шаблонов через JXLS и опциональная конвертация в PDF через headless-LibreOffice. Архитектура — тонкая оркестрация поверх двух SPI: `TemplateEngine` и `DocumentConverter`. Добавление новых форматов (например, DOCX или альтернативного PDF-конвертера) не меняет публичный API.

## Модули

| Артефакт | Назначение |
|---|---|
| `doc-engine-api` | Публичный API и SPI (`TemplateEngine`, `DocumentConverter`, контексты, типы запроса/результата, исключения). Без сторонних зависимостей — от него зависят реализаторы SPI. |
| `doc-engine-core` | Реализации поверх API: JXLS-движок, LibreOffice-конвертер и plain-Java входная точка `DocumentEngineBuilder`. Тянет JXLS/POI. |
| `doc-engine-spring-boot-starter` | Spring Boot 4.x auto-configuration (`@AutoConfiguration` + `AutoConfiguration.imports`). Тонкая обёртка поверх core. |
| `doc-engine-jodconverter` | Быстрая PDF-конверсия: пул долгоживущих LibreOffice-процессов (JODConverter). Зависит только от `doc-engine-api`. Опциональный модуль. |

## Требования

- Java 25+
- Maven 3.8+
- LibreOffice (`soffice` в PATH или явный путь) — только если нужна конвертация в PDF

## Установка из GitHub Packages

Артефакты публикуются в GitHub Packages. Для **публичных** пакетов GitHub всё равно требует аутентификации потребителя — это известное ограничение GitHub Packages, обойти его без переезда на Maven Central нельзя.

### 1. Personal Access Token

Создай classic PAT в GitHub с правом `read:packages` (или fine-grained token с доступом «Packages: Read»).

### 2. `~/.m2/settings.xml`

```xml
<settings>
    <servers>
        <server>
            <id>github-nikolaynn</id>
            <username>ВАШ_GITHUB_LOGIN</username>
            <password>ghp_xxxxxxxxxxxxxxxxxxxxxxxx</password>
        </server>
    </servers>
</settings>
```

### 3. `pom.xml` потребителя

```xml
<repositories>
    <repository>
        <id>github-nikolaynn</id>
        <url>https://maven.pkg.github.com/NikolayNN/doc-generator-engine</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.nikolaynn</groupId>
        <artifactId>doc-engine-spring-boot-starter</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

`id` репозитория в `<repositories>` должен совпадать с `id` сервера в `settings.xml`.

## Быстрый старт — plain Java

> `DocumentEngineBuilder` находится в пакете `io.github.nikolaynn.docengine.core` (модуль `doc-engine-core`).

```java
// полный дефолтный стек: JXLS + LibreOffice (soffice из PATH) + системный temp
DocumentEngine engine = DocumentEngineBuilder.create()
    .withDefaults()
    .build();

// или с явной настройкой компонентов — по-прежнему без импортов internal-классов:
DocumentEngine custom = DocumentEngineBuilder.create()
    .withJxlsEngine()
    .withLibreOfficeConverter(Path.of("/usr/bin/soffice"), Duration.ofSeconds(60), null)
    .withDefaultTempFileManager(Path.of("/tmp/doc-engine"), true)
    .build();

byte[] templateBytes = Files.readAllBytes(Path.of("invoice.xlsx"));

GenerationRequest request = new GenerationRequest(
    new TemplateReference.BytesRef(templateBytes, DocumentFormat.XLSX, "invoice"),
    Map.of(
        "customer", "ACME Corp",
        "items", List.of(
            Map.of("name", "Widget", "qty", 2, "price", new BigDecimal("100")),
            Map.of("name", "Gadget", "qty", 3, "price", new BigDecimal("50"))
        )
    ),
    DocumentFormat.PDF,
    GenerationOptions.defaults()
);

GenerationResult result = engine.generate(request);
// result.fileName(), result.mimeType(), result.content()
```

Опции и ссылки на шаблон можно собирать эргономичнее:

```java
GenerationOptions opts = GenerationOptions.builder()
    .fileNameHint("invoice")
    .timeout(Duration.ofSeconds(60))
    .build();

TemplateReference ref = TemplateReference.ofBytes(templateBytes, DocumentFormat.XLSX, "invoice");
// или из потока: TemplateReference.ofStream(in, DocumentFormat.XLSX, "invoice")
```

`GenerationOptions` — advisory-контракт: `timeout` применяет **только** процессный LibreOffice-конвертер (JXLS-рендер и пул JODConverter его игнорируют); `locale` и `engineHints` встроенный JXLS-движок не использует — их получает лишь кастомный `TemplateEngine` через `RenderContext` (в `ConvertContext` их нет, поэтому `DocumentConverter` их не видит).

Для больших документов есть стриминговые варианты — без буферизации всего результата в память:

```java
// в поток (например, HTTP-ответ); поток остаётся открытым — им владеет вызывающий
GenerationMetadata meta = engine.generateTo(request, response.getOutputStream());
response.setContentType(meta.mimeType());

// или сразу в файл (перезаписывает существующий)
engine.generateToFile(request, Path.of("/reports/invoice.pdf"));
```

Конвертер LibreOffice нужен только для генерации PDF. Если в `targetFormat` всегда `XLSX`, `withLibreOfficeConverter(...)` можно не вызывать (а `withDefaults()` добавляет его на всякий случай — при отсутствии `soffice` он просто не используется, пока не запрошена конвертация).

`DocumentEngine` и `TempFileManager` — `AutoCloseable`: `close()` удаляет отслеживаемые временные файлы и снимает shutdown-хук. Движок — application-scoped синглтон: создайте один раз и закройте при остановке приложения (в Spring это происходит автоматически при закрытии контекста).

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
`office-home` (автодетект), `pool-size` (1), `base-port` (2002),
`task-timeout` (120s), `task-queue-timeout` (30s),
`max-tasks-per-process` (200), `existing-process-action` (по умолчанию —
дефолт JODConverter, `KILL`).
Примечание: `GenerationOptions.timeout` этим конвертером не применяется —
таймаут задаётся на уровне пула.

> **Порты.** Каждый процесс пула слушает свой локальный TCP-порт:
> `base-port` … `base-port + pool-size - 1`. Если на одном хосте работают
> несколько приложений с пулом (или любой другой пользователь JODConverter),
> задайте им непересекающиеся диапазоны — иначе стартующий позже с действием
> `KILL` убьёт чужие LibreOffice-процессы на этих портах. `existing-process-action`
> принимает `FAIL`, `KILL`, `CONNECT`, `CONNECT_OR_KILL`.

## Быстрый старт — Spring Boot

Подключите стартер:

```xml
<dependency>
    <groupId>io.github.nikolaynn</groupId>
    <artifactId>doc-engine-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Бин `DocumentEngine` создаётся автоматически и инжектится в ваш сервис:

```java
@Service
@RequiredArgsConstructor
public class InvoiceService {
    private final DocumentEngine documentEngine;

    public GenerationResult render(byte[] templateBytes, Map<String, Object> data) {
        return documentEngine.generate(new GenerationRequest(
            new TemplateReference.BytesRef(templateBytes, DocumentFormat.XLSX, "invoice"),
            data,
            DocumentFormat.PDF,
            GenerationOptions.defaults()
        ));
    }
}
```

### Конфигурация (application.yml)

```yaml
doc-engine:
  temp-dir: /var/tmp/doc-engine        # null = системный temp
  cleanup-on-shutdown: true
  converter:
    libreoffice:
      enabled: true                    # false = PDF недоступен, soffice не нужен
      executable: /usr/bin/soffice     # null = искать в PATH
      timeout: 60s
      working-dir: /var/tmp/doc-engine
    jod:                                 # если подключён doc-engine-jodconverter
      enabled: true
      pool-size: 2
      base-port: 2002                    # у соседних приложений — свой диапазон
      task-timeout: 120s
```

> Свойства `doc-engine.*` снабжены config-metadata: IDE подсказывает ключи, типы и значения по умолчанию.

## Шаблоны (JXLS)

Шаблоны — обычные XLSX-файлы с JXLS-разметкой:

- **Поля.** `${name}` в ячейке — подстановка скаляров.
- **Циклы по строкам.** Комментарий JXLS в первой ячейке строки-образца: `jx:each(items="items", var="item", lastCell="C2")`. Строка раскрывается по списку, стили и границы сохраняются, последующие строки сдвигаются.
- **Условные блоки.** `jx:if(condition="...", lastCell="...")`.
- **Группировка, вложенные циклы, multisheet** — стандартные возможности JXLS.
- **Формулы.** `=A1+A2`, `=SUM(D2:D5)` остаются формулами; JXLS корректно сдвигает ссылки на расширяющиеся диапазоны. Движок ставит `setForceFormulaRecalculation(true)`, поэтому значения пересчитываются при открытии файла (Excel/LibreOffice) и при конвертации в PDF. POI-вычисление на стороне библиотеки не выполняется — это экономит память/CPU и не падает на функциях, которые POI не реализует.

Подробнее — документация JXLS 3.x: https://jxls.sourceforge.net/

## Расширение через SPI

Все точки расширения — в пакете `io.github.nikolaynn.docengine.spi`:

| SPI | Назначение |
|---|---|
| `TemplateEngine` | Рендеринг шаблона нового формата (например, DOCX). |
| `DocumentConverter` | Альтернативный конвертер (Gotenberg, JODConverter, Aspose). |
| `TemplateResolver` | Источник шаблона (classpath, S3, БД) — по умолчанию identity для `BytesRef`/`InputStreamRef`. |
| `TemplateValidator` | Проверка шаблона (в MVP — no-op). |
| `TempFileManager` | Управление временными файлами. |

В Spring-сценарии достаточно объявить свой бин — auto-configuration подхватит его в `List<TemplateEngine>`/`List<DocumentConverter>` или заменит дефолт по `@ConditionalOnMissingBean`. В plain-Java — добавить через `DocumentEngineBuilder`.

Выбор реализации — registry pattern: первый SPI, у которого `supports(...) == true`. Если подходящей реализации нет — `UnsupportedTemplateFormatException` / `UnsupportedConversionException`.

## Модель ошибок

Все исключения — unchecked, корень — `DocumentGenerationException`. Внутренние ошибки JXLS, POI и I/O **никогда** не утекают наружу: они оборачиваются в публичную иерархию.

```
DocumentGenerationException
  ├── InvalidGenerationRequestException
  ├── TemplateResolutionException
  ├── TemplateValidationException
  ├── TemplateRenderingException
  ├── UnsupportedTemplateFormatException
  ├── UnsupportedConversionException
  ├── DocumentConversionException
  └── TempFileException
```

Каждое исключение несёт `templateHint`, `sourceFormat`, `targetFormat`. Содержимое шаблона и подставляемые данные в сообщения не попадают (PII-safe).

## Сборка и тесты

```bash
mvn clean install
```

- Юнит-тесты `doc-engine-core` — без `soffice`, без сети.
- `LibreOfficeConverterIT` и `EndToEndTest#pdfRoundTripWithBuilder` запускаются только при наличии `soffice` в PATH (через `@EnabledIf`); иначе пропускаются и сборка не падает.
- `EndToEndTest#dumpSamplesToTarget` кладёт пары input/output XLSX в `target/e2e-samples/` — удобно открывать в Excel/LibreOffice для глазной проверки.

## Changelog

История изменений — в каталоге [changelog/](changelog/README.md).

## Дизайн

Полный design-документ — [docs/superpowers/specs/2026-05-26-doc-generator-engine-design.md](docs/superpowers/specs/2026-05-26-doc-generator-engine-design.md).
