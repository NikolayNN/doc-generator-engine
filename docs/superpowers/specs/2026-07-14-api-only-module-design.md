# API-only module split — Design

**Date:** 2026-07-14
**Status:** Approved (scope trimmed — see Scope)

## Goal

Extract the public API and SPI from `doc-engine-core` into a new, dependency-free
`doc-engine-api` module, so that SPI implementers (and any consumer that needs
only the contract) can depend on a tiny JDK-only artifact instead of dragging in
the whole JXLS/POI stack.

## Motivation

Today `doc-engine-core` bundles three concerns in one jar: the public **api**
package, the **spi** contracts, and the **internal** implementations (JXLS
engine, LibreOffice converter, temp-file manager, resolver, validator). The jar
therefore pulls `jxls`, `jxls-poi` (Apache POI ≈ 15 MB), `commons-jexl3`, and
`slf4j`.

`doc-engine-jodconverter` — the one existing SPI implementer — depends on
`doc-engine-core` purely to reference four JDK-only types (`DocumentFormat`,
`DocumentConversionException`, `ConvertContext`, `DocumentConverter`), yet
inherits the entire JXLS/POI transitive tree on its compile/runtime classpath.

Splitting the api/spi out gives:

- A clean **contract-vs-implementation** boundary: `doc-engine-api` is the public,
  stable surface; `doc-engine-core` holds swappable internals.
- `doc-engine-jodconverter`'s compile/runtime classpath sheds JXLS/POI entirely.
- A 1:1 package↔jar mapping (no split packages), leaving the door open for JPMS.

This is primarily an **architecture-cleanliness / extensibility** investment.
The tangible win today is narrow (jodconverter's transitive footprint, and a
JDK-only artifact for future third-party SPI implementers). The Spring Boot
starter and plain-Java `core` consumers see no change in what they pull.

## Scope

**In scope:**
- New `doc-engine-api` module (api + spi + api/exception, JDK-only).
- Relocate `DocumentEngineBuilder` out of the `api` package into a core-owned
  package (breaking import change — acceptable pre-1.0).
- Re-point `doc-engine-jodconverter` at `doc-engine-api` (compile) with a
  test-scoped `doc-engine-core` fallback.
- Minimal README note documenting the new `doc-engine-api` artifact and the
  builder's new package.

**Out of scope (explicitly deferred):**
- **`doc-engine-bom`** — deferred until real external consumers mix multiple
  `doc-engine-*` artifacts.
- **Version bump** — the reactor stays `0.1.0-SNAPSHOT`. No release is published
  by this work (nothing is tagged).

## Module topology & dependency graph

```
doc-generator-engine (parent, pom)  — version 0.1.0-SNAPSHOT (unchanged)
├── doc-engine-api      [NEW]  api/* + spi/* + api/exception/*   deps: NONE (JDK-only); test: junit, assertj
├── doc-engine-core            internal/* + core.DocumentEngineBuilder
│                              deps: doc-engine-api, jxls, jxls-poi, commons-jexl3, slf4j
├── doc-engine-jodconverter    deps: doc-engine-api (compile), jodconverter-local, slf4j
│                              +     doc-engine-core (TEST scope)
└── doc-engine-spring-boot-starter   unchanged (core + jodconverter + spring-boot-*)
```

- Reactor build order sorts automatically by the dep graph:
  `api → core → jodconverter → starter`.
- `doc-engine-core` depends on `doc-engine-api` at **compile** scope, so every
  existing core consumer keeps seeing all api/spi types transitively — **no
  change for core users**.
- `doc-engine-jodconverter`'s **compile/runtime** classpath no longer contains
  JXLS/POI. They reappear only under **test** scope, supplied transitively by the
  test-scoped `doc-engine-core` dependency (which the jodconverter tests need for
  `DefaultTempFileManager` and for building `XSSFWorkbook` fixtures).

## File-level plan

### Main sources: move `doc-engine-core` → `doc-engine-api` (package names unchanged)

Move the entire `api` package **except** `DocumentEngineBuilder.java`, plus all of
`api/exception` and `spi`:

- `api/DocumentEngine.java`
- `api/DocumentFormat.java`
- `api/GenerationMetadata.java`
- `api/GenerationOptions.java`
- `api/GenerationRequest.java`
- `api/GenerationResult.java`
- `api/TemplateReference.java`
- `api/exception/DocumentConversionException.java`
- `api/exception/DocumentGenerationException.java`
- `api/exception/InvalidGenerationRequestException.java`
- `api/exception/TempFileException.java`
- `api/exception/TemplateRenderingException.java`
- `api/exception/TemplateResolutionException.java`
- `api/exception/TemplateValidationException.java`
- `api/exception/UnsupportedConversionException.java`
- `api/exception/UnsupportedTemplateFormatException.java`
- `spi/ConvertContext.java`
- `spi/DocumentConverter.java`
- `spi/RenderContext.java`
- `spi/ResolvedTemplate.java`
- `spi/TempFileManager.java`
- `spi/TemplateEngine.java`
- `spi/TemplateResolver.java`
- `spi/TemplateValidator.java`

Package declarations (`io.github.nikolaynn.docengine.api`,
`io.github.nikolaynn.docengine.spi`) are **unchanged** — only the jar boundary
moves. Target path in the new module:
`doc-engine-api/src/main/java/io/github/nikolaynn/docengine/{api,spi}/…`.

### Builder relocation (the breaking change)

- Move `doc-engine-core/…/api/DocumentEngineBuilder.java` →
  `doc-engine-core/…/core/DocumentEngineBuilder.java`.
- Change its package declaration from `io.github.nikolaynn.docengine.api` to
  **`io.github.nikolaynn.docengine.core`**.
- Add an `import io.github.nikolaynn.docengine.api.DocumentEngine;` (and any other
  api/spi types it references, e.g. `TempFileManager`, `TemplateResolver`,
  `TemplateValidator`, `TemplateEngine`, `DocumentConverter`) now that they live
  in a different package than the builder. Its imports of `internal.*` are
  unchanged.

`DocumentEngineBuilder` stays in `doc-engine-core` because it constructs internal
impls (`JxlsTemplateEngine`, `LibreOfficeConverter`, `InputStreamTemplateResolver`,
`DefaultTempFileManager`, `NoopTemplateValidator`, `DefaultDocumentEngine`).

### Test sources: move to `doc-engine-api` (JDK/junit/assertj-only, verified)

- `api/DocumentFormatTest.java`
- `api/GenerationOptionsTest.java`
- `api/GenerationTypesTest.java`
- `api/exception/DocumentGenerationExceptionTest.java`
- `spi/ContextRecordsTest.java`

These reference only api types (`DocumentFormat`) plus junit + assertj. Target:
`doc-engine-api/src/test/java/io/github/nikolaynn/docengine/{api,spi}/…`.

### Test sources: stay in `doc-engine-core`

- `api/TemplateReferenceTest.java` — exercises the builder + JXLS. Stays in core;
  add `import io.github.nikolaynn.docengine.core.DocumentEngineBuilder;`.
- `api/DocumentEngineBuilderTest.java` — move to
  `core/DocumentEngineBuilderTest.java`, package
  `io.github.nikolaynn.docengine.core`; it tests the builder directly.
- `EndToEndTest.java` — update its import
  `io.github.nikolaynn.docengine.api.DocumentEngineBuilder` →
  `io.github.nikolaynn.docengine.core.DocumentEngineBuilder`.
- All `internal/*` tests and `support/*` helpers — unchanged.

## POM changes

### Root `pom.xml`
- Add `<module>doc-engine-api</module>` to `<modules>` (before `doc-engine-core`).
- Add a `dependencyManagement` entry for `doc-engine-api` (and, for consistency,
  `doc-engine-core`) at `${project.version}`.

### `doc-engine-api/pom.xml` (new)
- Parent = `doc-generator-engine`, artifactId `doc-engine-api`.
- **No** compile dependencies (JDK-only).
- Test dependencies: `junit-jupiter`, `assertj-core` (both already version-managed
  in the parent).
- Inherits the parent build (jacoco prepare-agent/report, failsafe). No special
  config needed.

### `doc-engine-core/pom.xml`
- Add a dependency on `doc-engine-api` (compile scope, `${project.version}`).
- Keep `jxls`, `jxls-poi`, `commons-jexl3`, `slf4j` — the internals still need them.

### `doc-engine-jodconverter/pom.xml`
- Change the `doc-engine-core` dependency to `doc-engine-api` (compile).
- Add `doc-engine-core` at **`<scope>test</scope>`** (`${project.version}`) so the
  existing tests keep compiling (`DefaultTempFileManager`, and POI's `XSSFWorkbook`
  transitively via jxls-poi).

### `doc-engine-spring-boot-starter/pom.xml`
- Unchanged. It still depends on `doc-engine-core` (for the internal impls its
  auto-config wires) and `doc-engine-jodconverter`.

## README changes (minimal)

- Add `doc-engine-api` to the artifact/coordinates documentation as the module
  SPI implementers depend on.
- Note that the plain-Java entry point `DocumentEngineBuilder` now lives in
  `io.github.nikolaynn.docengine.core`.
- Leave version references at `0.1.0` (no bump in this work). Existing builder
  code snippets do not show imports, so they remain correct.

## Verification / acceptance criteria

1. `JAVA_HOME=C:\Program Files\Java\jdk-17`; `mvn -B verify` is **green across all
   four modules**, all existing test suites pass.
2. `mvn -B -pl doc-engine-jodconverter dependency:tree` shows `jxls`, `jxls-poi`,
   `org.apache.poi:*`, and `commons-jexl3` **only under `test` scope** — absent
   from compile/runtime.
3. `doc-engine-api` has **no third-party** entries on its compile classpath
   (`dependency:tree` shows only JDK / the module itself).
4. `doc-engine-core`, `doc-engine-jodconverter`, and
   `doc-engine-spring-boot-starter` consumers observe no behavioral change; the
   only source-visible change is the builder's package.
5. CI (`build.yml`: `verify` + `libreoffice-it`) is green after push.

## Non-goals / risks

- **Breaking change:** `DocumentEngineBuilder`'s package moves from `…api` to
  `…core`. Acceptable pre-1.0; documented in README. `v0.1.0` remains the last
  release; this work stays on `0.1.0-SNAPSHOT`.
- **jodconverter test-scoped core dep** is a mild coupling (tests reach back into
  core), justified by reusing `DefaultTempFileManager` and POI fixtures rather
  than duplicating them. It does not leak to downstream consumers.
