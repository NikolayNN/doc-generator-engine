# Changelog

Все заметные изменения проекта документируются в этом каталоге: сводка по версиям здесь,
подробности — в файле соответствующей версии.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версионирование — [SemVer](https://semver.org/lang/ru/).

## [0.1.0] — 2026-05-27

### Добавлено

- Первый релиз: генерация документов по XLSX-шаблонам (JXLS) с опциональной конвертацией в PDF через headless LibreOffice.
- Публичный API и SPI (движки шаблонов, конвертеры, резолверы, temp-файлы), plain-Java **`DocumentEngineBuilder`** и стартер Spring Boot 2.7.
- CI на push/PR и публикация в GitHub Packages по тегу `v*` (sources + javadoc), лицензия Apache-2.0.

[0.1.0]: ./0.1.0.md
