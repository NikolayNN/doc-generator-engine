# DX/интерфейс public API (кластер B) — дизайн

Дата: 2026-07-14
Статус: одобрен

## 1. Проблема

Публичный API движка работоспособен, но недоработан по эргономике и честности
контракта:

- `GenerationOptions` конструируется только 4-арг каноническим конструктором
  (`fileNameHint, timeout, locale, engineHints`) — легко перепутать порядок
  `timeout`/`locale`; билдера нет.
- `TemplateReference` создаётся через `new TemplateReference.BytesRef(...)` —
  многословно, светит вложенным типом.
- `locale`/`engineHints`/`timeout` принимаются, но пользователь не может понять,
  что именно применяется: встроенный JXLS-движок `locale`/`engineHints` не
  использует, `timeout` honor'ится только процессным LibreOffice-конвертером
  (не JXLS-render, не jod-пул). Опции не «мёртвые» — они пробрасываются в
  SPI-контексты (`RenderContext`/`ConvertContext`), поэтому кастомные
  `TemplateEngine`/`DocumentConverter` их получают и могут honor'ить. Дефект —
  незнание контракта, а не отсутствие функциональности.
- Свойства стартера `doc-engine.*` не имеют config-metadata → нет автодополнения
  и описаний в IDE.
- Javadoc на публичном API неполон.

## 2. Решения (зафиксированы с пользователем)

1. **Игнорируемые опции — документируем как advisory (вариант A).** Никакой
   реализации `locale`/`engineHints` во встроенном JXLS и никакого таймаута на
   синхронный render. Обоснование: POI `LocaleUtil` JVM-глобален и
   потоко-небезопасен (грабли в конкурентном веб-сервисе); у `engineHints` нет
   словаря (реализация = спекулятивный YAGNI); таймаут на быстрый синхронный
   render требует отдельного потока и прерывания POI (сложно, бессмысленно), а
   на медленном шаге (конверсия LibreOffice) таймаут уже работает. Поля остаются
   forward-looking SPI-хуками.
2. **Health-probe/actuator — выкинут (YAGNI).** Нет сценария; тянет
   actuator-зависимость; из-за ленивого старта пула осмысленный health свёлся бы
   к deployment-specific пробе «доступен ли soffice».
3. **Остальные пункты — в объёме:** builder `GenerationOptions`, фабрики
   `TemplateReference.ofBytes/ofStream`, Spring config metadata, Javadoc.

## 3. Не входит в объём (non-goals)

- Реализация `locale`/`engineHints` во встроенном JXLS-движке.
- Таймаут на JXLS-render и на jod-пул (последний — задокументированный non-goal
  прошлой фичи).
- Удаление каких-либо полей `GenerationOptions` (обратная совместимость).
- Actuator/health-indicator.
- Изменение SPI-сигнатур.

## 4. Обратная совместимость

Все изменения — только добавления: новые методы (`builder()`, фабрики), новые
Javadoc-комментарии, одна optional-зависимость (config-processor), новый
ресурс-файл metadata. Канонический конструктор `GenerationOptions`,
`GenerationOptions.defaults()`, публичные записи `BytesRef`/`InputStreamRef`
остаются без изменений. Существующий код и тесты не ломаются.

## 5. Детальный дизайн

### 5.1 `GenerationOptions` — builder + advisory Javadoc

Файл: `doc-engine-core/.../api/GenerationOptions.java`.

- Добавить вложенный `public static final class Builder` и `public static Builder builder()`.
  Методы билдера (каждый возвращает `this`):
  - `fileNameHint(String)`
  - `timeout(Duration)`
  - `locale(Locale)`
  - `engineHint(String key, Object value)` — аккумулирует в `LinkedHashMap`
    (лениво инициализируемую); повторные вызовы добавляют/перезаписывают ключ.
  - `engineHints(Map<String, Object> hints)` — заменяет накопленную карту (null → пустая).
  - `build()` → `new GenerationOptions(fileNameHint, timeout, locale, engineHints)`.
- Билдер стартует со всеми полями `null`/пустая карта; `build()` полагается на
  существующий compact-конструктор (он уже нормализует `engineHints`).
- Канонический конструктор и `defaults()` сохраняются.
- Javadoc:
  - на record — назначение; ссылка на `builder()`/`defaults()`.
  - `fileNameHint` — как формируется имя выходного файла.
  - `timeout` — «honor'ится **только** процессным LibreOffice-конвертером; JXLS-render
    и пул JODConverter его не применяют».
  - `locale` — «advisory: встроенный JXLS-движок не применяет; кастомный
    `TemplateEngine` получает через `RenderContext` и может honor'ить».
  - `engineHints` — «advisory generic pass-through: встроенные компоненты не
    используют; доступны кастомным `TemplateEngine`/`DocumentConverter` через
    SPI-контексты».

Тест (TDD): `GenerationOptionsTest`
- builder с полным набором полей эквивалентен каноническому конструктору;
- `engineHint` аккумулирует несколько ключей; `engineHints(map)` заменяет;
- `builder().build()` эквивалентен `defaults()` по значению полей;
- null в сеттерах терпимы (не бросает; `engineHints` → пустая карта).

### 5.2 `TemplateReference` — фабрики + Javadoc

Файл: `doc-engine-core/.../api/TemplateReference.java`.

- `static TemplateReference ofBytes(byte[] bytes, DocumentFormat sourceFormat, String hint)`
  → `new BytesRef(bytes, sourceFormat, hint)`.
- `static TemplateReference ofStream(InputStream stream, DocumentFormat sourceFormat, String hint)`
  → `new InputStreamRef(stream, sourceFormat, hint)`.
- Возвращают тип интерфейса `TemplateReference`. Записи остаются публичными.
- Javadoc на интерфейс и обе фабрики (когда что использовать; `ofStream`
  потребляется один раз при resolve).

Тест (TDD): дополнить `TemplateReferenceTest`
- `ofBytes(...)` возвращает `BytesRef` с теми же полями и равен конструкторной
  форме (equals уже value-based);
- `ofStream(...)` возвращает `InputStreamRef` с теми же полями (сравнение по
  полям stream/format/hint — stream identity).

### 5.3 Spring config metadata

Файлы:
- `doc-engine-spring-boot-starter/pom.xml` — добавить `org.springframework.boot:spring-boot-configuration-processor`
  со `scope=provided` (или `optional=true`); он мёржит additional-файл в
  генерируемый `spring-configuration-metadata.json` на этапе компиляции.
  **Без процессора additional-файл не подхватывается — процессор обязателен.**
- `doc-engine-spring-boot-starter/.../DocEngineProperties.java` — Javadoc на все
  компоненты (temp-dir, cleanup-on-shutdown, converter.libreoffice.*,
  converter.jod.*); процессор превращает их в `description`.
- `doc-engine-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
  (новый) — записи `properties` с `name`, `type`, `description`, `defaultValue`
  для всех ключей, где дефолт — конкретный литерал (compact-конструкторы
  задают дефолты кодом, процессор их не выводит):

  | name | type | defaultValue |
  |---|---|---|
  | `doc-engine.temp-dir` | `java.nio.file.Path` | — (null = системный temp) |
  | `doc-engine.cleanup-on-shutdown` | `java.lang.Boolean` | `true` |
  | `doc-engine.converter.libreoffice.enabled` | `java.lang.Boolean` | `true` |
  | `doc-engine.converter.libreoffice.executable` | `java.nio.file.Path` | — (null = PATH) |
  | `doc-engine.converter.libreoffice.timeout` | `java.time.Duration` | `60s` |
  | `doc-engine.converter.libreoffice.working-dir` | `java.nio.file.Path` | — |
  | `doc-engine.converter.jod.enabled` | `java.lang.Boolean` | `true` |
  | `doc-engine.converter.jod.office-home` | `java.nio.file.Path` | — (автодетект) |
  | `doc-engine.converter.jod.pool-size` | `java.lang.Integer` | `1` |
  | `doc-engine.converter.jod.task-timeout` | `java.time.Duration` | `120s` |
  | `doc-engine.converter.jod.task-queue-timeout` | `java.time.Duration` | `30s` |
  | `doc-engine.converter.jod.max-tasks-per-process` | `java.lang.Integer` | `200` |

Тест (guard, не строгий TDD — метаданные это IDE-tooling без рантайм-поведения):
`ConfigMetadataTest` в стартере грузит сгенерированный
`/META-INF/spring-configuration-metadata.json` из classpath (процессор кладёт его
в `target/classes` при компиляции) и проверяет, что присутствуют ключевые
свойства (`doc-engine.converter.jod.pool-size` и т.п.) с ожидаемыми
`defaultValue`. Это верифицирует всю цепочку Javadoc → processor → merge.

### 5.4 Javadoc на остальном публичном API

Файлы (публичный пакет `api`), не затронутые выше:
`DocumentEngineBuilder`, `GenerationRequest`, `GenerationResult`,
`GenerationMetadata`, `DocumentFormat`, иерархия исключений в `api/exception`.
`DocumentEngine` уже документирован. Javadoc краткий, точный, без «воды».
Тестов нет (документация; проверяется компиляцией в `mvn verify`).

### 5.5 README

Файл: `README.md`.
- В секции про plain-Java/варианты — упомянуть `GenerationOptions.builder()` и
  `TemplateReference.ofBytes(...)`/`ofStream(...)`.
- Короткая заметка (advisory): какие поля `GenerationOptions` honor'ятся
  встроенными компонентами (timeout — только процессный LibreOffice; locale/hints
  — только кастомные движки через SPI).

## 6. Разбивка на задачи (для плана)

1. `GenerationOptions` builder + advisory Javadoc (+`GenerationOptionsTest`, TDD).
2. `TemplateReference` фабрики + Javadoc (+`TemplateReferenceTest`, TDD).
3. Spring config metadata: config-processor + Javadoc на `DocEngineProperties`
   + `additional-spring-configuration-metadata.json` (+`ConfigMetadataTest`, guard).
4. Javadoc на остальные публичные классы (`api`, `api/exception`).
5. README + финальный `mvn -B verify`.

Задачи независимы; порядок — как перечислено.

## 7. Тестирование

- 1–2: строгий TDD (тест падает до кода; equals уже value-based после прошлого
  батча — фабрики/билдер сравниваются по значению).
- 3: guard-тест читает сгенерированные metadata из classpath и проверяет
  наличие/дефолты ключей.
- 4–5: документация; корректность — зелёный `mvn -B verify` (компиляция +
  существующие тесты).
- Порог покрытия core (0.70 веток) остаётся зелёным: новый код (builder, фабрики)
  покрыт тестами.

## 8. Риски

- **config-processor и дубли метаданных:** additional-файл задаёт `defaultValue`;
  описания идут из Javadoc через процессор. При конфликте атрибутов побеждает
  additional-файл — приемлемо. Проверяется guard-тестом.
- **Javadoc-объём:** пункт 4 самый широкий по поверхности, но низконюансный;
  не трогает поведение.
- **Порядок в билдере:** билдер устраняет главный риск канонического
  конструктора (перепутанные `timeout`/`locale`).
