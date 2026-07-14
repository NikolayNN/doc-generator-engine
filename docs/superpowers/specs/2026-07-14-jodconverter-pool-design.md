# Пул процессов LibreOffice через JODConverter — дизайн

Дата: 2026-07-14
Статус: одобрен

## 1. Проблема

`LibreOfficeConverter` запускает новый процесс `soffice --headless --convert-to`
на каждую конверсию. Cold start LibreOffice — 2–6 секунд, сама конверсия —
доли секунды. Практический потолок — порядка одной конверсии в секунду на хост
независимо от числа ядер. Целевой профиль использования — веб-сервисы с
постоянным потоком PDF-генераций, для них это основной источник латентности.

## 2. Решения (зафиксированы с пользователем)

1. **Профиль нагрузки:** постоянный поток конверсий — пул оправдан.
2. **Подход:** интеграция `org.jodconverter:jodconverter-local` (пул
   долгоживущих процессов LibreOffice, диспетчеризация по UNO). Свой пул не
   пишем — это повторная реализация JODConverter.
3. **Размещение:** новый Maven-модуль `doc-engine-jodconverter`. Core не
   получает новых зависимостей; кто хочет пул — добавляет артефакт.
4. **Активация в стартере:** автоматическая по classpath. Модуль на classpath →
   jod-конвертер становится основным, процессный `libreOfficeConverter`
   отключается. Откат — `doc-engine.converter.jod.enabled=false`.

## 3. Не входит в объём (non-goals)

- Фолбэк с jod-конвертера на процессный при ошибке: роутинг движка берёт
  первый подходящий конвертер без ретрая по остальным; менять это сейчас не
  планируется.
- Новые пары форматов: конвертер поддерживает ту же пару XLSX → PDF.
- Изменение SPI `DocumentConverter.convert(...)` — сигнатура остаётся.

## 4. Модуль `doc-engine-jodconverter`

### 4.1 Зависимости

- `doc-engine-core` (compile) — ради SPI и исключений.
- `org.jodconverter:jodconverter-local` — версия и точный состав транзитивных
  зависимостей (UNO-библиотеки LibreOffice) проверяются на этапе реализации;
  версия выносится в `<properties>` корневого pom.

### 4.2 Публичный класс `JodDocumentConverter`

`io.github.nikolaynn.docengine.jod.JodDocumentConverter
    implements DocumentConverter, AutoCloseable`

- `supports(from, to)` — только `XLSX → PDF` (паритет с процессным конвертером).
- Конфигурация — вложенный record `Config` со статическим `Config.builder()`;
  публичная, без классов jodconverter в сигнатурах. Поля:
  - `officeHome` (Path, null = автодетект JODConverter);
  - `poolSize` (int, число процессов; транслируется в portNumbers);
  - `taskExecutionTimeout` (Duration);
  - `taskQueueTimeout` (Duration);
  - `maxTasksPerProcess` (int, рестарт процесса после N задач);
  - `workingDir` (Path, null = системный temp).
- Для тестируемости конструктор (package-private или публичный) принимает
  готовый `org.jodconverter.core.office.OfficeManager`; публичная фабрика
  строит `LocalOfficeManager` из `Config`.

### 4.3 Жизненный цикл

- **Ленивый старт:** пул стартует при первой конверсии (double-checked
  locking). Сборка движка не платит секунды старта и не падает при
  отсутствующем `soffice`.
- **Явный `start()`** — для прогрева на старте приложения по желанию.
- **`close()`** — идемпотентно останавливает `OfficeManager` (гасит процессы
  пула). Ошибки остановки логируются, не бросаются.
- Конверсия после `close()` — `IllegalStateException`.

### 4.4 Конверсия и маппинг ошибок

- Вход/выход — файлы (как в SPI): `LocalConverter` конвертирует входной файл в
  managed-файл из `ctx.tempFileManager().createTempFile(...)`.
- `OfficeException` и наследники → `DocumentConversionException` с
  `templateHint/source/target`; таймаут задачи → фабрика
  `DocumentConversionException.timeout(...)` (флаг `isTimeout()`).
- Таймаут задачи: в JODConverter `taskExecutionTimeout` задаётся на уровне
  менеджера (пер-задачное переопределение штатно не поддерживается), поэтому
  действует значение конфига; `ctx.timeout()` этим конвертером не применяется,
  что фиксируется в Javadoc. Если на этапе реализации обнаружится штатный
  способ пер-задачного таймаута — используется `ctx.timeout()` с фолбэком на
  конфиг.
- Если после успешного вызова файл-результат отсутствует или пуст —
  `DocumentConversionException` (паритет с процессным конвертером).

## 5. Изменения в core: каскад close на конвертеры

- `DocumentConverter extends AutoCloseable` + `@Override default void close() {}`
  (паттерн уже применён к `TempFileManager`). Существующие реализации не
  ломаются.
- `DefaultDocumentEngine.close()` дополнительно вызывает `close()` у всех
  конвертеров (лог + продолжение при исключении), затем закрывает
  `tempFiles`. Обоснование: plain-Java-пользователь строит движок билдером и
  должен уметь остановить пул одним `engine.close()`.

## 6. Стартер: автоактивация

### 6.1 Новая автоконфигурация

`JodConverterAutoConfiguration`:

- `@ConditionalOnClass(JodDocumentConverter.class)`;
- `@AutoConfigureBefore(DocEngineAutoConfiguration.class)` + регистрация в
  `AutoConfiguration.imports` и `spring.factories` (оба механизма, как сейчас);
- бин `jodDocumentConverter`:
  - `@ConditionalOnMissingBean(DocumentConverter.class)` — любой
    пользовательский конвертер вытесняет его;
  - `@ConditionalOnProperty(prefix = "doc-engine.converter.jod",
    name = "enabled", havingValue = "true", matchIfMissing = true)`.
- Зависимость стартера на `doc-engine-jodconverter` — `optional`.

### 6.2 Свойства `doc-engine.converter.jod.*`

| Свойство | Тип | Дефолт |
|---|---|---|
| `enabled` | boolean | `true` (при наличии класса) |
| `office-home` | Path | null (автодетект) |
| `pool-size` | int | 1 |
| `task-timeout` | Duration | 120s |
| `task-queue-timeout` | Duration | 30s |
| `max-tasks-per-process` | int | 200 |

Добавляются в `DocEngineProperties` (вложенный record `Jod`).

### 6.3 Процессный конвертер становится настоящим фолбэком по конфигурации

Условие бина `libreOfficeConverter` меняется с
`@ConditionalOnMissingBean(name = "libreOfficeConverter")` на
`@ConditionalOnMissingBean(DocumentConverter.class)`: он создаётся только если
никакого другого `DocumentConverter` в контексте нет. Побочный эффект —
устраняется недокументированная магия имён бинов: пользовательский конвертер с
любым именем вытесняет дефолт. Существующий тест `userConverterReplacesDefault`
адаптируется под типовую семантику.

### 6.4 Остановка пула

Бин `JodDocumentConverter` — `AutoCloseable`; Spring вызывает `close()` как
inferred destroy method при закрытии контекста. Отдельного кода не требуется.

## 7. Тестирование

### 7.1 Юнит-тесты (без LibreOffice, бегут везде)

`org.jodconverter.core.office.OfficeManager` — интерфейс, мокается:

- ленивый старт: `manager.start()` вызывается один раз при первой конверсии,
  не при создании;
- `close()` вызывает `manager.stop()`; идемпотентность; конверсия после
  close → `IllegalStateException`;
- `OfficeException` из менеджера → `DocumentConversionException`;
- `supports`: только XLSX→PDF.

Happy-path конверсии юнит-тестами не покрывается (требует реального UNO) —
это осознанно отдано интеграционному тесту.

### 7.2 Интеграционные тесты (гейт `@EnabledIf(sofficeAvailable)`)

`JodDocumentConverterIT` в новом модуле, тот же гейт, что у существующих IT:

- одиночная конверсия XLSX→PDF через реальный пул (файл существует, >100 байт);
- смоук на конкурентность: 4 параллельные конверсии — все успешны
  (без таймингов, только корректность);
- выполняется в CI-джобе `libreoffice-it` (LibreOffice установлен), локально
  без soffice — скипается.

### 7.3 Тесты стартера

- с классом на classpath (тестовый classpath стартера включает модуль):
  контекст содержит `jodDocumentConverter`, не содержит `libreOfficeConverter`;
- `doc-engine.converter.jod.enabled=false` → наоборот;
- пользовательский `DocumentConverter`-бин вытесняет оба дефолта;
- биндинг свойств `doc-engine.converter.jod.*`.

### 7.4 Инфраструктура

Новый модуль добавляется в `<modules>` корневого pom; failsafe и jacoco
наследуются из корневой конфигурации автоматически. Порог покрытия (70% веток)
остаётся только в core.

## 8. Документация

- README: строка в таблице модулей; секция «Быстрая конверсия PDF: пул
  LibreOffice» — когда брать модуль, пример plain-Java
  (`new JodDocumentConverter(config)` + `addConverter`), свойства стартера,
  прогрев через `start()`;
- примечание, что процессный конвертер остаётся дефолтом без модуля.

## 9. Порядок реализации (крупно)

1. Core: `DocumentConverter.close()` default + каскад в
   `DefaultDocumentEngine.close()` (TDD).
2. Модуль `doc-engine-jodconverter`: конфиг, ленивый старт, маппинг ошибок,
   юнит-тесты на моках `OfficeManager` (TDD), IT под гейтом.
3. Стартер: свойства, `JodConverterAutoConfiguration`, смена условия
   `libreOfficeConverter`, тесты контекста (TDD).
4. README + прогон CI (джоб `libreoffice-it` подтверждает реальный пул).

## 10. Риски

- **Тяжёлые транзитивные зависимости jodconverter-local** — изолированы в
  отдельном модуле; проверить лицензии/размер на этапе реализации.
- **Порты UNO**: параллельные CI-джобы/приложения на одном хосте могут
  конфликтовать портами — дефолтный `poolSize=1` и конфигурируемые порты
  JODConverter снижают риск; в CI используется один менеджер.
- **Поведение при недоступном soffice**: ленивый старт переносит ошибку на
  первую конверсию; желающие fail-fast вызывают `start()` на старте приложения
  (отражено в README).
