# API-only Module Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the public API and SPI out of `doc-engine-core` into a new dependency-free `doc-engine-api` module so SPI implementers no longer pull JXLS/POI.

**Architecture:** Move the `api` (minus the builder), `api/exception`, and `spi` packages verbatim into a new JDK-only `doc-engine-api` module; `doc-engine-core` depends on it (compile) and keeps only `internal/*` plus the relocated `DocumentEngineBuilder` (moved to a new `…docengine.core` package). `doc-engine-jodconverter` re-points its compile dependency at `doc-engine-api` and keeps `doc-engine-core` at test scope for its fixtures. Package names of moved types are unchanged — only the jar boundary moves. No behavior changes; the existing test suites are the safety net.

**Tech Stack:** Java 17, Maven multi-module (reactor), JUnit 5, AssertJ, Mockito, JaCoCo, JXLS/POI (core only), JODConverter (jodconverter module only).

## Global Constraints

- **JDK 17 required.** The machine's default `JAVA_HOME` is JDK 11, which fails with "invalid target release: 17". Every Maven command must run with JDK 17. PowerShell: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn …`  (Git Bash: `JAVA_HOME='/c/Program Files/Java/jdk-17' mvn …`).
- **Commit trailer** on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- **No version bump, no BOM.** The reactor stays `0.1.0-SNAPSHOT`. `v0.1.0` remains the last release; nothing is published by this work.
- **Package names of moved types are unchanged:** `io.github.nikolaynn.docengine.api` and `io.github.nikolaynn.docengine.spi`. Only `DocumentEngineBuilder` changes package → `io.github.nikolaynn.docengine.core`.
- **`doc-engine-api` must have ZERO third-party compile dependencies** (JDK-only). Its only dependencies are `junit-jupiter` and `assertj-core` at test scope.
- **`doc-engine-jodconverter`'s compile/runtime classpath must be free of JXLS / Apache POI / commons-jexl3.** They may appear only under `test` scope (supplied transitively by the test-scoped `doc-engine-core`).
- **`doc-engine-core` enforces a JaCoCo BRANCH coverage floor of 0.70 (BUNDLE).** `mvn verify` must stay green including this check. Measured post-move value is ≈ 79% (73 covered / 92 total branches over the classes that remain), so no new tests are needed for coverage — but if `verify` ever reports below 0.70, add targeted branch tests for the internal classes; **do not lower the floor.**
- Work directly on `master`, consistent with this session's established workflow for the prior clusters.

---

### Task 1: Scaffold the `doc-engine-api` module and register it in the reactor

Creates the empty module and wires it into the parent so later tasks have a destination. Ends with a green full-reactor build (the empty module produces an empty jar; nothing has moved yet).

**Files:**
- Create: `doc-engine-api/pom.xml`
- Modify: `pom.xml` (root) — `<modules>` and `<dependencyManagement>`

**Interfaces:**
- Produces: a buildable Maven module `io.github.nikolaynn:doc-engine-api:0.1.0-SNAPSHOT`, and root-managed versions for `doc-engine-api` and `doc-engine-core` (so later module poms can omit `<version>`).

- [ ] **Step 1: Create `doc-engine-api/pom.xml`**

Create `doc-engine-api/pom.xml` with exactly this content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.nikolaynn</groupId>
        <artifactId>doc-generator-engine</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>doc-engine-api</artifactId>
    <name>Document Generator Engine — API</name>
    <description>Public API and SPI contracts. JDK-only, no third-party dependencies.</description>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Register the module in the root `pom.xml`**

In `pom.xml`, change the `<modules>` block (currently lines 39-43) so `doc-engine-api` is listed first:

```xml
    <modules>
        <module>doc-engine-api</module>
        <module>doc-engine-core</module>
        <module>doc-engine-jodconverter</module>
        <module>doc-engine-spring-boot-starter</module>
    </modules>
```

- [ ] **Step 3: Add root dependency management for the two internal modules**

In `pom.xml`, inside `<dependencyManagement><dependencies>`, immediately **before** the existing `doc-engine-jodconverter` entry (currently lines 124-128), insert:

```xml
            <dependency>
                <groupId>io.github.nikolaynn</groupId>
                <artifactId>doc-engine-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>io.github.nikolaynn</groupId>
                <artifactId>doc-engine-core</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 4: Verify the reactor builds with the new empty module**

Run (PowerShell): `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B verify`

Expected: `BUILD SUCCESS`, reactor now lists 4 modules including `doc-engine-api`, which produces `doc-engine-api/target/doc-engine-api-0.1.0-SNAPSHOT.jar` (empty). All existing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-api/pom.xml pom.xml
git commit -m "build: scaffold empty doc-engine-api module in the reactor

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Move api/spi/exception into `doc-engine-api`, relocate the builder, wire core→api

Moves all api/spi/exception main sources and their JDK-only unit tests into `doc-engine-api`, relocates `DocumentEngineBuilder` (+ its test) from the `api` package to a new `core` package in `doc-engine-core`, fixes the two dependent test imports, and adds core's compile dependency on api. This is a pure structural move — the existing suites verify no behavior changed. Ends with a green full `mvn verify` (including core's 0.70 JaCoCo branch floor).

**Files:**
- Move (git mv), core → api, **main sources** (package declarations unchanged):
  - `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/spi/` (whole directory: `ConvertContext.java`, `DocumentConverter.java`, `RenderContext.java`, `ResolvedTemplate.java`, `TempFileManager.java`, `TemplateEngine.java`, `TemplateResolver.java`, `TemplateValidator.java`)
  - `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/exception/` (whole directory: 9 exception classes)
  - `doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/{DocumentEngine,DocumentFormat,GenerationMetadata,GenerationOptions,GenerationRequest,GenerationResult,TemplateReference}.java` (7 files — **not** `DocumentEngineBuilder.java`)
- Move (git mv), core → api, **test sources**:
  - `…/test/…/api/DocumentFormatTest.java`, `…/api/GenerationOptionsTest.java`, `…/api/GenerationTypesTest.java` → api module `…/api/`
  - `…/test/…/api/exception/DocumentGenerationExceptionTest.java` → api module `…/api/exception/`
  - `…/test/…/spi/ContextRecordsTest.java` → api module `…/spi/`
- Relocate within `doc-engine-core` (package change):
  - Main: `…/api/DocumentEngineBuilder.java` → `…/core/DocumentEngineBuilder.java`
  - Test: `…/test/…/api/DocumentEngineBuilderTest.java` → `…/test/…/core/DocumentEngineBuilderTest.java`
- Modify (import fix): `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/EndToEndTest.java:4`
- Modify (import add): `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/TemplateReferenceTest.java`
- Modify: `doc-engine-core/pom.xml` (add api dependency)

**Interfaces:**
- Consumes: the empty `doc-engine-api` module and root-managed versions from Task 1.
- Produces: `io.github.nikolaynn.docengine.core.DocumentEngineBuilder` (was `…api.DocumentEngineBuilder`) — same public API (`create()`, `withDefaults()`, `withJxlsEngine()`, `withLibreOfficeConverter(…)`, `withDefaultTempFileManager(Path,boolean)`, `tempFileManager/templateResolver/templateValidator/addTemplateEngine/addConverter`, `build()`), only the package changed. All api/spi types now live in the `doc-engine-api` jar under unchanged package names.

- [ ] **Step 1: Create the destination directories in `doc-engine-api`**

```bash
mkdir -p doc-engine-api/src/main/java/io/github/nikolaynn/docengine
mkdir -p doc-engine-api/src/test/java/io/github/nikolaynn/docengine/api/exception
mkdir -p doc-engine-api/src/test/java/io/github/nikolaynn/docengine/spi
```

- [ ] **Step 2: Move the `spi` and `api/exception` directories (main sources) into api**

```bash
git mv doc-engine-core/src/main/java/io/github/nikolaynn/docengine/spi \
       doc-engine-api/src/main/java/io/github/nikolaynn/docengine/spi
mkdir -p doc-engine-api/src/main/java/io/github/nikolaynn/docengine/api
git mv doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/exception \
       doc-engine-api/src/main/java/io/github/nikolaynn/docengine/api/exception
```

- [ ] **Step 3: Move the 7 api value types (not the builder) into api**

Run from the repository root (absolute-from-root paths, no `cd`):

```bash
SRC=doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api
DST=doc-engine-api/src/main/java/io/github/nikolaynn/docengine/api
for f in DocumentEngine DocumentFormat GenerationMetadata GenerationOptions GenerationRequest GenerationResult TemplateReference; do
  git mv "$SRC/$f.java" "$DST/$f.java"
done
```

Verify only `DocumentEngineBuilder.java` remains in the core `api` dir:

```bash
ls doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api
```
Expected: `DocumentEngineBuilder.java` only.

- [ ] **Step 4: Move the 5 JDK-only unit tests into api**

```bash
BASE=doc-engine-core/src/test/java/io/github/nikolaynn/docengine
API=doc-engine-api/src/test/java/io/github/nikolaynn/docengine
git mv $BASE/api/DocumentFormatTest.java        $API/api/DocumentFormatTest.java
git mv $BASE/api/GenerationOptionsTest.java      $API/api/GenerationOptionsTest.java
git mv $BASE/api/GenerationTypesTest.java        $API/api/GenerationTypesTest.java
git mv $BASE/api/exception/DocumentGenerationExceptionTest.java $API/api/exception/DocumentGenerationExceptionTest.java
git mv $BASE/spi/ContextRecordsTest.java         $API/spi/ContextRecordsTest.java
```

- [ ] **Step 5: Relocate `DocumentEngineBuilder` to the `core` package**

```bash
mkdir -p doc-engine-core/src/main/java/io/github/nikolaynn/docengine/core
git mv doc-engine-core/src/main/java/io/github/nikolaynn/docengine/api/DocumentEngineBuilder.java \
       doc-engine-core/src/main/java/io/github/nikolaynn/docengine/core/DocumentEngineBuilder.java
```

Then edit the top of the moved file — change the package and add the `DocumentEngine` import. The file's top (currently `package …api;` followed by the internal + spi imports) becomes exactly:

```java
package io.github.nikolaynn.docengine.core;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.internal.DefaultDocumentEngine;
import io.github.nikolaynn.docengine.internal.jxls.JxlsTemplateEngine;
import io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverter;
import io.github.nikolaynn.docengine.internal.resolver.InputStreamTemplateResolver;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.internal.validator.NoopTemplateValidator;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
```

Everything below the imports (the class body) is unchanged.

- [ ] **Step 6: Relocate `DocumentEngineBuilderTest` to the `core` package**

```bash
mkdir -p doc-engine-core/src/test/java/io/github/nikolaynn/docengine/core
git mv doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/DocumentEngineBuilderTest.java \
       doc-engine-core/src/test/java/io/github/nikolaynn/docengine/core/DocumentEngineBuilderTest.java
```

Then edit the top of the moved test — change the package (it now sits in `core`, so the api types it used via same-package need explicit imports). Replace the current header (`package …api;` plus its imports) so the top of the file reads exactly:

```java
package io.github.nikolaynn.docengine.core;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.GenerationResult;
import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import io.github.nikolaynn.docengine.support.TemplateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
```

The test methods below are unchanged (they reference `DocumentEngineBuilder` by simple name, now resolved from the same `core` package).

- [ ] **Step 7: Fix `EndToEndTest` builder import**

In `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/EndToEndTest.java`, change line 4 from:

```java
import io.github.nikolaynn.docengine.api.DocumentEngineBuilder;
```
to:
```java
import io.github.nikolaynn.docengine.core.DocumentEngineBuilder;
```

- [ ] **Step 8: Add the builder import to `TemplateReferenceTest`**

`TemplateReferenceTest` stays in core (it uses the builder + JXLS fixtures) and previously referenced `DocumentEngineBuilder` via the same `api` package. Add an explicit import. In `doc-engine-core/src/test/java/io/github/nikolaynn/docengine/api/TemplateReferenceTest.java`, immediately after the line `import io.github.nikolaynn.docengine.spi.TemplateResolver;` insert:

```java
import io.github.nikolaynn.docengine.core.DocumentEngineBuilder;
```

- [ ] **Step 9: Add the api dependency to `doc-engine-core/pom.xml`**

In `doc-engine-core/pom.xml`, immediately after the opening `<dependencies>` tag (line 16), insert (version is managed by the root, so omit it):

```xml
        <dependency>
            <groupId>io.github.nikolaynn</groupId>
            <artifactId>doc-engine-api</artifactId>
        </dependency>
```

- [ ] **Step 10: Verify the full reactor builds and all tests pass**

Run (PowerShell): `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B clean verify`

Expected: `BUILD SUCCESS`. `doc-engine-api` compiles the moved sources and runs the 5 moved tests; `doc-engine-core` compiles against the api jar and runs the remaining tests including the relocated `DocumentEngineBuilderTest`, `EndToEndTest`, and `TemplateReferenceTest`. The `doc-engine-core` JaCoCo `check-coverage` execution passes (branch coverage ≈ 0.79, ≥ 0.70 floor). If the JaCoCo check fails below 0.70, add targeted branch tests for the uncovered internal-class branches; do not lower the floor.

- [ ] **Step 11: Verify `doc-engine-api` has no third-party compile dependencies**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-api dependency:tree`

Expected: the only dependencies listed are `org.junit.jupiter:junit-jupiter` and `org.assertj:assertj-core` (with their transitives), **every one at `:test` scope**. No `org.jxls`, `org.apache.poi`, `org.apache.commons`, or `org.slf4j` entries appear at any scope.

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "refactor: extract api+spi into doc-engine-api; move builder to core package

Moves the api (except the builder), api/exception, and spi packages verbatim
into the new dependency-free doc-engine-api module, and relocates
DocumentEngineBuilder from the api package to io.github.nikolaynn.docengine.core.
Package names of moved types are unchanged; only the jar boundary and the
builder's package moved. doc-engine-core now depends on doc-engine-api.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Re-point `doc-engine-jodconverter` at `doc-engine-api`

Swaps jodconverter's compile dependency from `doc-engine-core` to `doc-engine-api`, and keeps `doc-engine-core` at **test** scope (its tests need `DefaultTempFileManager` and, transitively, POI's `XSSFWorkbook`). This drops JXLS/POI from jodconverter's compile/runtime classpath — the concrete win of the split.

**Files:**
- Modify: `doc-engine-jodconverter/pom.xml` (dependencies)

**Interfaces:**
- Consumes: `doc-engine-api` (compile) for `DocumentFormat`, `DocumentConversionException`, `ConvertContext`, `DocumentConverter`; `doc-engine-core` (test) for `DefaultTempFileManager` and POI fixtures.

- [ ] **Step 1: Replace the core dependency with api (compile) + core (test)**

In `doc-engine-jodconverter/pom.xml`, replace the existing dependency block (currently lines 17-21):

```xml
        <dependency>
            <groupId>io.github.nikolaynn</groupId>
            <artifactId>doc-engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
```

with (versions managed by the root, so omitted):

```xml
        <dependency>
            <groupId>io.github.nikolaynn</groupId>
            <artifactId>doc-engine-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.nikolaynn</groupId>
            <artifactId>doc-engine-core</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Verify jodconverter compiles and its tests pass**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-jodconverter -am verify`

Expected: `BUILD SUCCESS`. Main code compiles against api only; test code still compiles (`DefaultTempFileManager`, `XSSFWorkbook` available via the test-scoped core). All jodconverter tests pass.

- [ ] **Step 3: Verify JXLS/POI are absent from the compile classpath**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B -pl doc-engine-jodconverter dependency:tree`

Expected (inspect the scope suffix — `groupId:artifactId:type:version:scope` — on each line):
- `io.github.nikolaynn:doc-engine-api`, `org.jodconverter:jodconverter-local`, and `org.slf4j:slf4j-api` are `:compile`.
- Every `org.jxls:*`, `org.apache.poi:*`, and `org.apache.commons:commons-jexl3` line ends with `:test` (they arrive only via the test-scoped `doc-engine-core`) — none appear at `:compile`.

- [ ] **Step 4: Commit**

```bash
git add doc-engine-jodconverter/pom.xml
git commit -m "build: re-point doc-engine-jodconverter at doc-engine-api

Main code now depends only on the JDK-only doc-engine-api; doc-engine-core
drops to test scope for the fixtures (DefaultTempFileManager, POI). JXLS/POI
leave jodconverter's compile/runtime classpath.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Document the new module and run final verification

Adds the minimal README note (new `doc-engine-api` artifact + the builder's new package) and runs the full acceptance build.

**Files:**
- Modify: `README.md` (module table + a builder-package note)

**Interfaces:**
- Consumes: the completed module split from Tasks 1-3.

- [ ] **Step 1: Update the module table in `README.md`**

Replace the module table (currently lines 9-13):

```markdown
| Артефакт | Назначение |
|---|---|
| `doc-engine-core` | Чистая Java-библиотека (без Spring). Публичный API, SPI, JXLS- и LibreOffice-реализации. |
| `doc-engine-spring-boot-starter` | Spring Boot 2.7 auto-configuration (совместима с 3.x). Тонкая обёртка поверх core. |
| `doc-engine-jodconverter` | Быстрая PDF-конверсия: пул долгоживущих LibreOffice-процессов (JODConverter). Опциональный модуль. |
```

with:

```markdown
| Артефакт | Назначение |
|---|---|
| `doc-engine-api` | Публичный API и SPI (`TemplateEngine`, `DocumentConverter`, контексты, типы запроса/результата, исключения). Без сторонних зависимостей — от него зависят реализаторы SPI. |
| `doc-engine-core` | Реализации поверх API: JXLS-движок, LibreOffice-конвертер и plain-Java входная точка `DocumentEngineBuilder`. Тянет JXLS/POI. |
| `doc-engine-spring-boot-starter` | Spring Boot 2.7 auto-configuration (совместима с 3.x). Тонкая обёртка поверх core. |
| `doc-engine-jodconverter` | Быстрая PDF-конверсия: пул долгоживущих LibreOffice-процессов (JODConverter). Зависит только от `doc-engine-api`. Опциональный модуль. |
```

- [ ] **Step 2: Note the builder's package under the plain-Java quickstart**

In `README.md`, immediately after the heading `## Быстрый старт — plain Java` (line 64), insert a blank line and this note:

```markdown
> `DocumentEngineBuilder` находится в пакете `io.github.nikolaynn.docengine.core` (модуль `doc-engine-core`).
```

- [ ] **Step 3: Final full verification**

Run: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; mvn -B clean verify`

Expected: `BUILD SUCCESS` across all 4 modules; every existing test suite passes; `doc-engine-core` JaCoCo branch check passes.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document doc-engine-api module and the builder's core package

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Acceptance criteria (whole branch)

1. `mvn -B clean verify` (JDK 17) is green across `doc-engine-api`, `doc-engine-core`, `doc-engine-jodconverter`, `doc-engine-spring-boot-starter`; all existing suites pass.
2. `doc-engine-api` compile tree has no third-party entries (Task 2, Step 11).
3. `doc-engine-jodconverter` compile tree has no `org.jxls` / `org.apache.poi` / `commons-jexl3`; they appear only under test scope (Task 3, Step 3).
4. `doc-engine-core` JaCoCo BRANCH coverage stays ≥ 0.70 (measured ≈ 0.79).
5. The only source-visible API change is `DocumentEngineBuilder`'s package (`…api` → `…core`); all other public types keep their package names.
6. After push, CI (`build.yml`: `verify` + `libreoffice-it`) is green.
