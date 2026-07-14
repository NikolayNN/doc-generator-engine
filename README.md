# Document Generator Engine

Java-библиотека для генерации документов по офисным шаблонам. Приложение готовит данные и выбирает шаблон, библиотека возвращает готовый документ (имя, MIME, байты).

В MVP поддерживается рендеринг XLSX-шаблонов через JXLS и опциональная конвертация в PDF через headless-LibreOffice. Архитектура — тонкая оркестрация поверх двух SPI: `TemplateEngine` и `DocumentConverter`. Добавление новых форматов (например, DOCX или альтернативного PDF-конвертера) не меняет публичный API.

## Модули

| Артефакт | Назначение |
|---|---|
| `doc-engine-core` | Чистая Java-библиотека (без Spring). Публичный API, SPI, JXLS- и LibreOffice-реализации. |
| `doc-engine-spring-boot-starter` | Spring Boot 2.7 auto-configuration (совместима с 3.x). Тонкая обёртка поверх core. |

## Требования

- Java 17+
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

```java
DocumentEngine engine = DocumentEngineBuilder.create()
    .tempFileManager(new DefaultTempFileManager(Path.of("/tmp/doc-engine"), true))
    .addTemplateEngine(new JxlsTemplateEngine())
    .addConverter(new LibreOfficeConverter(null, Duration.ofSeconds(60), null))
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

`LibreOfficeConverter` нужен только для генерации PDF. Если в `targetFormat` всегда `XLSX`, его можно не добавлять.

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
```

## Шаблоны (JXLS)

Шаблоны — обычные XLSX-файлы с JXLS-разметкой:

- **Поля.** `${name}` в ячейке — подстановка скаляров.
- **Циклы по строкам.** Комментарий JXLS в первой ячейке строки-образца: `jx:each(items="items", var="item", lastCell="C2")`. Строка раскрывается по списку, стили и границы сохраняются, последующие строки сдвигаются.
- **Условные блоки.** `jx:if(condition="...", lastCell="...")`.
- **Группировка, вложенные циклы, multisheet** — стандартные возможности JXLS.
- **Формулы.** `=A1+A2`, `=SUM(D2:D5)` остаются формулами; JXLS корректно сдвигает ссылки на расширяющиеся диапазоны. После рендера движок вызывает `FormulaEvaluator.evaluateAll()` и `setForceFormulaRecalculation(true)`, поэтому в XLSX лежат и формулы, и пересчитанные значения.

Подробнее — документация JXLS 2.x: https://jxls.sourceforge.net/

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

## Дизайн

Полный design-документ — [docs/superpowers/specs/2026-05-26-doc-generator-engine-design.md](docs/superpowers/specs/2026-05-26-doc-generator-engine-design.md).
