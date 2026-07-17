# Changelog

Все заметные изменения проекта документируются в этом каталоге: сводка по версиям здесь,
подробности — в файле соответствующей версии.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версионирование — [SemVer](https://semver.org/lang/ru/).

## [0.2.0] — 2026-07-17

### Добавлено

- Модуль **`doc-engine-jodconverter`**: пул долгоживущих LibreOffice-процессов (JODConverter) с авто-конфигурацией.
- Потоковый вывод **`generateTo(OutputStream)`** / **`generateToFile(Path)`**; `AutoCloseable`-жизненный цикл движка.
- Билдер `GenerationOptions`, фабрики `TemplateReference.ofBytes/ofStream`, Spring-метаданные конфигурации.
- CI: Windows-job, LibreOffice-, changelog- и tag-on-master-гейты релиза, maven-enforcer, Maven Wrapper, порог покрытия JaCoCo, воспроизводимые артефакты, пиновка actions, Dependabot-группы, dependency-review.

### Изменено

- **Breaking:** пакеты `com.example.docengine` → `io.github.nikolaynn.docengine`; новый артефакт **`doc-engine-api`**; платформа — Java 25 + Spring Boot 4.1 (было: Boot 2.7); JXLS 2.x → 3.1.0.

### Исправлено

- Управление LibreOffice-процессом и кодировка stderr, null-значения в данных, temp-каталог при первом использовании, value equality `BytesRef`.

### Безопасность

- Сняты CVE-пины 2.x-эры после миграции на JXLS 3.1.0; JEXL 3.7.0 с SECURE-дефолтами.

## [0.1.0] — 2026-05-27

### Добавлено

- Первый релиз: генерация документов по XLSX-шаблонам (JXLS) с опциональной конвертацией в PDF через headless LibreOffice.
- Публичный API и SPI (движки шаблонов, конвертеры, резолверы, temp-файлы), plain-Java **`DocumentEngineBuilder`** и стартер Spring Boot 2.7.
- CI на push/PR и публикация в GitHub Packages по тегу `v*` (sources + javadoc), лицензия Apache-2.0.

[0.2.0]: ./0.2.0.md
[0.1.0]: ./0.1.0.md
