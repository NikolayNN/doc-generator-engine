# doc-generator-engine

Java-библиотека: генерация документов из XLSX-шаблонов (JXLS 3) с опциональной
конвертацией в PDF через headless LibreOffice. Multi-module Maven, публикация
в GitHub Packages. Подробное описание модулей и API — в README.md.

## Сборка

- Требуются JDK 25 и Maven ≥ 3.8.4 (проверяет maven-enforcer-plugin на фазе validate).
- На этой машине системный JDK старее — перед любым `mvn` задавайте JAVA_HOME:
  - PowerShell: `$env:JAVA_HOME = 'C:\Users\Nikolay\.jdks\jdk-25.0.3+9'`
  - Git Bash: `export JAVA_HOME='C:\Users\Nikolay\.jdks\jdk-25.0.3+9'`
  - Симптом без этого: enforcer-ошибка `requireJavaVersion` (раньше — `invalid target release: 25`).
- Полная сборка с тестами: `./mvnw -B verify` (Maven зафиксирован wrapper-ом,
  3.9.16 — системный `mvn` не обязателен; JAVA_HOME нужен по-прежнему).
- В PowerShell goal-аргументы вида `-Dkey=value` местами съедаются парсером —
  используйте stop-parsing: `mvn --% -B versions:set -DnewVersion=0.3.0`.

## Модули

| Модуль | Роль |
|---|---|
| `doc-engine-api` | публичный API + SPI, без сторонних зависимостей |
| `doc-engine-core` | реализации: JXLS-движок, LibreOffice-конвертер, `DocumentEngineBuilder` |
| `doc-engine-spring-boot-starter` | автоконфигурация Spring Boot 4.x поверх core |
| `doc-engine-jodconverter` | пул тёплых LibreOffice-процессов (JODConverter), зависит только от api |

## Тесты

- LibreOffice-тесты гейтятся `@EnabledIf(... #sofficeAvailable)`: без `soffice`
  в PATH молча скипаются. Локально без LibreOffice это норма; в CI job
  `libreoffice-it` и релизный workflow ставят LibreOffice и проверяют
  `soffice --version`, чтобы гейт не скипнулся молча.
- Эталонные XLSX-шаблоны перегенерируются запуском тестов с
  `-Dregenerate.samples=true` (`SampleTemplateGenerator`).

## Релиз и changelog

- Релиз: пуш тега `v*` → `.github/workflows/release.yml`. Workflow падает,
  если нет `changelog/<версия>.md`; содержимое этого файла становится notes
  GitHub-релиза (draft создаётся до deploy, публикуется после).
- Changelog — русский Keep a Changelog в `changelog/`: сводка по версиям в
  `changelog/README.md`, детали в файле версии. Есть скилл `writing-changelog`.

## Дизайн-решения

- Любой пользовательский бин `DocumentConverter` в стартере намеренно замещает
  оба встроенных конвертера (не аддитивно) — см. README, раздел про SPI.
- Спеки и планы фич — в `docs/superpowers/specs` и `docs/superpowers/plans`.
