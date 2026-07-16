# JXLS 3.1.0 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Перевести рендер XLSX с мёртвой ветки JXLS 2.13.0 на JXLS 3.1.0, убрав ручные CVE-пины и exclusions, без изменения публичного API и поведения.

**Architecture:** Вся миграция локализована в `JxlsTemplateEngine` (единственный класс, использующий JXLS) + POM-правки. Сетка существующих тестов (JxlsTemplateEngineTest, EndToEndTest, DefaultDocumentEngineTest) пиняет поведение; перед свапом она усиливается тестом на null-значения в data.

**Tech Stack:** Java 25, Maven, JXLS 3.1.0 (`org.jxls:jxls`, `org.jxls:jxls-poi`), JEXL 3.7.0, POI 5.5.1 (транзитивно).

## Global Constraints

- `JAVA_HOME = C:\Users\Nikolay\.jdks\jdk-25.0.3+9` для всех mvn-команд (иначе «invalid target release: 25»).
- В PowerShell mvn с `-Dkey=value`, содержащими точки, вызывать через `mvn --% …` (иначе аргумент режется по точке).
- Все существующие тесты остаются зелёными БЕЗ правок; правка существующего теста = стоп и разбор.
- Coverage-гейт core: branch ≥ 0.70 (входит в `verify`).
- Публичный API модулей не меняется; разметка шаблонов (`jx:area`, `jx:each`, `${…}`) не меняется.
- Шаблоны потребителей могут содержать null-значения в data (контракт `GenerationRequest`).

---

### Task 1: Pin-тест — null-значения в data рендерятся пустой ячейкой

**Files:**
- Modify: `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/internal/jxls/JxlsTemplateEngineTest.java`

**Interfaces:**
- Consumes: `TemplateFixtures.simpleFields()` (шаблон `${greeting}`/`${name}` в A1/B1), `engine.render(ResolvedTemplate, Map, RenderContext)`, хелпер `ctx()`.
- Produces: тест `rendersNullDataValueAsBlank`, который обязан остаться зелёным после свапа на JXLS 3.

- [ ] **Step 1: Написать тест (пин текущего поведения — на 2.13.0 он должен сразу пройти)**

Добавить в `JxlsTemplateEngineTest` (import `java.util.HashMap`):

```java
@Test
void rendersNullDataValueAsBlank() throws Exception {
    // GenerationRequest documents that data may contain null values; a null
    // must render as an empty cell, not the literal token or "null"
    var template = new ResolvedTemplate(TemplateFixtures.simpleFields(),
        DocumentFormat.XLSX, "null-value");
    Map<String, Object> data = new HashMap<>();
    data.put("greeting", "Hello");
    data.put("name", null);

    Path out = engine.render(template, data, ctx());

    try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
        Sheet sh = wb.getSheetAt(0);
        assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Hello");
        var nameCell = sh.getRow(0).getCell(1);
        assertThat(nameCell == null ? "" : nameCell.toString())
            .as("null data value must render as an empty cell")
            .isEmpty();
    }
}
```

- [ ] **Step 2: Прогнать тест — GREEN на JXLS 2.13.0 (это пин, а не RED-тест)**

Run: `mvn -B -pl doc-engine-core test -Dtest=JxlsTemplateEngineTest` (через `--%`)
Expected: `Tests run: 9, Failures: 0`

- [ ] **Step 3: Commit**

```bash
git add doc-engine-core/src/test/java/io/github/nikolaynn/docengine/internal/jxls/JxlsTemplateEngineTest.java
git commit -m "test: pin null-data-value rendering ahead of the JXLS 3 migration"
```

---

### Task 2: POM-свап на JXLS 3.1.0 и чистка пинов

**Files:**
- Modify: `pom.xml` (корневой)
- Modify: `doc-engine-core/pom.xml`

**Interfaces:**
- Produces: реактор на jxls/jxls-poi 3.1.0; core не компилируется (ожидаемо) до Task 3.

- [ ] **Step 1: Корневой pom.xml**

В `<properties>`: `jxls.version`/`jxls-poi.version` `2.13.0 → 3.1.0`; `commons-jexl3.version` пока НЕ трогать (Task 4); удалить блок CVE-пинов — свойства `poi.version`, `commons-beanutils.version`, `commons-compress.version` вместе с комментарием.

В `<dependencyManagement>`: удалить записи `org.apache.poi:poi`, `poi-ooxml`, `poi-ooxml-lite`, `commons-beanutils:commons-beanutils`, `org.apache.commons:commons-compress`, `org.slf4j:jcl-over-slf4j` (всё это чинилось для мёртвой 2.x; в 3.1.0 приходит естественно). Запись `org.apache.commons:commons-jexl3` оставить.

- [ ] **Step 2: doc-engine-core/pom.xml**

У зависимости `org.jxls:jxls` удалить весь блок `<exclusions>` (logback-core и commons-logging в jxls 3 не тянутся/исключены upstream'ом). Зависимость `org.apache.commons:commons-jexl3` удалить целиком вместе с exclusions — кодом JEXL не используется, версию держит dependencyManagement.

- [ ] **Step 3: Убедиться, что core перестал компилироваться (естественный RED свапа)**

Run: `mvn -B -pl doc-engine-core -am test-compile`
Expected: COMPILATION ERROR в `JxlsTemplateEngine.java` — `package org.jxls.util does not exist` (JxlsHelper/Context удалены в 3.x).

---

### Task 3: Переписать JxlsTemplateEngine на JXLS 3 API

**Files:**
- Modify: `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/internal/jxls/JxlsTemplateEngine.java`

**Interfaces:**
- Consumes: `JxlsPoiTemplateFillerBuilder.newInstance()`, `.withTemplate(InputStream)`, `.withRecalculateFormulasOnOpening(boolean)`, `.withExceptionThrower()`, `.buildAndFill(Map<String,Object>, File)` — сигнатуры проверены javap по jxls-poi-3.1.0.
- Produces: прежний контракт `TemplateEngine.render(ResolvedTemplate, Map, RenderContext) -> Path`.

- [ ] **Step 1: Заменить тело render() и импорты**

```java
package io.github.nikolaynn.docengine.internal.jxls;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.TemplateRenderingException;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import org.jxls.transform.poi.JxlsPoiTemplateFillerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

public class JxlsTemplateEngine implements TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(JxlsTemplateEngine.class);

    @Override
    public boolean supports(DocumentFormat sourceFormat) {
        return sourceFormat == DocumentFormat.XLSX;
    }

    @Override
    public Path render(ResolvedTemplate template, Map<String, Object> data, RenderContext ctx) {
        if (template.sourceFormat() != DocumentFormat.XLSX) {
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "unsupported source format: " + template.sourceFormat(), null);
        }

        Path out = ctx.tempFileManager().createTempFile("doc-engine-", ".xlsx");
        try (InputStream in = new ByteArrayInputStream(template.bytes())) {
            JxlsPoiTemplateFillerBuilder.newInstance()
                .withTemplate(in)
                // formula recalculation is delegated to the opening application
                // (Excel / LibreOffice); POI-side evaluation would re-parse the whole
                // workbook and fails on functions POI does not implement
                .withRecalculateFormulasOnOpening(true)
                // the default PoiExceptionLogger only LOGS render errors and lets a
                // broken file through; the error model requires them to fail loudly
                .withExceptionThrower()
                .buildAndFill(data, out.toFile());

            log.debug("rendered template {} to {}", template.hint(), out);
            return out;
        } catch (IOException | RuntimeException e) {
            ctx.tempFileManager().delete(out);
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "JXLS failed to render template", e);
        }
    }
}
```

- [ ] **Step 2: Прогнать тесты core — GREEN, включая pin-тест Task 1**

Run: `mvn -B -pl doc-engine-core -am test`
Expected: `JxlsTemplateEngineTest Tests run: 9, Failures: 0`; `EndToEndTest Tests run: 8, Skipped: 1`; суммарно core `Tests run: 72, Failures: 0, Skipped: 1`.

Если `rendersNullDataValueAsBlank` упал (JXLS 3 пишет «null»/токен) — не менять тест: добавить в render() предобработку data (заменять null на "" через копию map) и зафиксировать это комментарием.

- [ ] **Step 3: Commit**

```bash
git add pom.xml doc-engine-core/pom.xml doc-engine-core/src/main/java/io/github/nikolaynn/docengine/internal/jxls/JxlsTemplateEngine.java
git commit -m "build!: migrate rendering to JXLS 3.1.0, drop 2.x-era CVE pins"
```

---

### Task 4: JEXL 3.7.0 (hardening песочницы)

**Files:**
- Modify: `pom.xml` (корневой, свойство `commons-jexl3.version`)

- [ ] **Step 1: Поднять пин**

`<commons-jexl3.version>3.3</commons-jexl3.version>` → `<commons-jexl3.version>3.7.0</commons-jexl3.version>` с комментарием: `<!-- ≥3.6.4: изоляция RESTRICTED-песочницы (JEXL-462); 3.7.0 добавляет SECURE-дефолты -->`

- [ ] **Step 2: Прогнать тесты core**

Run: `mvn -B -pl doc-engine-core -am test`
Expected: GREEN как в Task 3 / Step 2. Если выражения (`item.qty * item.price`, чтение свойств) падают из-за SECURE-дефолтов 3.7.0 — откатить на `3.6.4`, прогнать снова (ожидается GREEN) и записать причину в спеку.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: pin JEXL to 3.7.0 for the hardened expression sandbox"
```

---

### Task 5: Аудит дерева зависимостей

- [ ] **Step 1: Снять дерево**

Run: `mvn -B dependency:tree -pl doc-engine-core`
Expected (compile scope): `org.apache.poi:poi(-ooxml,-ooxml-lite):5.5.1`, `commons-beanutils:1.11.0`, `commons-compress:1.28.0`, `commons-jexl3:3.7.0` (или 3.6.4), `xmlbeans:5.3.0`; НЕТ `logback-core`, НЕТ `jcl-over-slf4j`; JCL-провайдер максимум один (`commons-logging:1.3.5`, если jxls-poi тянет его compile — допустимо, это единственный провайдер).

Если что-то не сошлось — вернуть точечный пин/exclusion в корневой POM и зафиксировать отклонение комментарием там же.

---

### Task 6: Документация

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Обновить упоминания JXLS 2.x**

Строка «В MVP поддерживается рендеринг XLSX-шаблонов через JXLS…» — без изменений (версия не упомянута). Строку «Подробнее — документация JXLS 2.x: https://jxls.sourceforge.net/» заменить на «Подробнее — документация JXLS 3.x: https://jxls.sourceforge.net/». Упоминание «стандартные возможности JXLS» — без изменений.

- [ ] **Step 2: Commit**

```bash
git add README.md docs/superpowers/specs/2026-07-16-jxls-3-migration-design.md docs/superpowers/plans/2026-07-16-jxls-3-migration.md
git commit -m "docs: JXLS 3 migration spec, plan and README refresh"
```

---

### Task 7: Полная верификация

- [ ] **Step 1: Полный реактор**

Run: `mvn clean verify -B`
Expected: BUILD SUCCESS; тесты: api 26, core 72 (1 skipped), jodconverter 16 (+2 IT skipped), starter 14; «All coverage checks have been met».

- [ ] **Step 2: Сверить, что публичная поверхность не изменилась**

Run: `git diff HEAD~4 --stat -- doc-engine-api/src/main`
Expected: пусто (api-модуль не тронут).
