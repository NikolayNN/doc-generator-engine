# JXLS 3.1.0 Migration — Design

**Дата:** 2026-07-16 · **Статус:** approved

## Зачем

JXLS 2.x — мёртвая ветка (последний релиз 2.14.0, декабрь 2023): исправления и
обновления транзитивов туда больше не попадают, из-за чего в корневом POM живут
ручные CVE-пины (POI 5.5.1, beanutils 1.11.0, compress 1.28.0) и exclusions
логгинг-мусора (logback-core, commons-logging). JXLS 3.1.0 (март 2026) — живая
ветка: сама тянет POI 5.5.1, beanutils 1.11.0, compress 1.28.0, JEXL 3.6.2 и
не тащит логовых бэкендов.

## Что меняется

| Область | 2.13.0 | 3.1.0 |
|---|---|---|
| Вход рендера | `JxlsHelper.getInstance()` + `Context.putVar` + `Transformer` | `JxlsPoiTemplateFillerBuilder.newInstance().withTemplate(in)…buildAndFill(map, file)` |
| Пересчёт формул при открытии | вручную `PoiTransformer.getWorkbook().setForceFormulaRecalculation(true)` | опция `withRecalculateFormulasOnOpening(true)` |
| Ошибки рендера | исключения POI/JXLS пробрасываются | по умолчанию `PoiExceptionLogger` **глотает** ошибки → обязателен `withExceptionThrower()` (ставит `PoiExceptionThrower`) |
| Иерархия ошибок | RuntimeException | `org.jxls.common.JxlsException extends RuntimeException` — ловится существующим `catch (IOException \| RuntimeException)` |
| Разметка шаблонов | `jx:area`, `jx:each`, `${…}` | без изменений — фикстуры и шаблоны потребителей не трогаем |

Проверено по байткоду jxls/jxls-poi 3.1.0 из Maven Central (javap), не по докам.

## Решения

1. **`withExceptionThrower()` обязателен** — контракт модели ошибок: рендер либо
   удался, либо бросил `TemplateRenderingException`; молча записанный битый файл
   недопустим.
2. **JEXL пиним на 3.7.0** (hardening песочницы JEXL-450/462/464; jxls 3.1.0
   сам тянет 3.6.2). Бамп отдельной задачей после зелёных тестов на 3.6.2, чтобы
   изолировать переменные; при падении — откат на 3.6.4 с фиксацией причины.
3. **CVE-пины и exclusions из корневого POM удаляем** — их версии теперь
   приходят из jxls 3.1.0 естественно; подтверждаем через `dependency:tree`.
   Прямую зависимость core на `commons-jexl3` убираем (кодом JEXL не используем),
   версию держит dependencyManagement.
4. **Публичный API библиотеки не меняется** — миграция целиком внутри
   `JxlsTemplateEngine`; сетка из 130 тестов обязана остаться зелёной без правок
   (кроме одного нового теста, пиняющего null-значения в data до миграции).

## Отклонения по факту миграции (as-shipped)

1. **JEXL-бамп слился со свапом.** План изолировал бамп в отдельную задачу, но
   jxls 3.1.0 несовместим с JEXL 3.3 уже в рантайме (`JexlBuilder.create`
   падает) — 3.7.0 поставлен сразу; SECURE-дефолты рендер не сломали.
2. **jcl-over-slf4j вернулся, теперь runtime-зависимостью core.** jxls 3.x, в
   отличие от 2.x, не тащит JCL-провайдера вовсе (у них commons-logging в
   test-scope): JEXL/beanutils падают с `NoClassDefFoundError: LogFactory`.
   Мост объявлен явно в core с комментарием.
3. **`withRecalculateFormulasBeforeSaving(false)` обязателен.** В JXLS 3 флаг
   по умолчанию включён (проверено байткодом): POI-вычисление всех формул при
   записи — падает на функциях без POI-реализации (поймал pin-тест PHONETIC).
4. **JXLS 3 мутирует переданную data-map** (`ContextImpl` пишет run-переменные
   `jx:each` прямо в неё) — движок отдаёт одноразовую null-tolerant копию,
   unmodifiable-map из `GenerationRequest` продолжает работать.

## Риски

- JXLS 3 может рендерить null-значения данных иначе (контракт: «data может
  содержать null») → перед свапом добавляем pin-тест на текущее поведение.
- JEXL 3.7.0 включает SECURE-дефолты (запрет `new`, side-effects) — наши
  шаблоны используют только чтение свойств и арифметику; проверяется тестами,
  откат на 3.6.4 предусмотрен.
- Coverage-гейт core (branch ≥ 0.70) — новый `render()` проще старого,
  падение веток не ожидается; гейт входит в verify.
