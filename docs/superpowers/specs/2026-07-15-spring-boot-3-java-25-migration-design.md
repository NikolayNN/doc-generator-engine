# Spring Boot 3.5.14 + Java 25 migration — Design

**Date:** 2026-07-15
**Status:** Approved

## Goal

Move the whole `doc-generator-engine` reactor from **Java 17 + Spring Boot 2.7.18**
to **Java 25 + Spring Boot 3.5.14** in a single big-bang change, updating every
build/test tool that would otherwise break on Java 25 class files, and
modernizing the Spring Boot starter's auto-configuration to the Boot 3 idiom.

## Motivation

The project is a published library (GitHub Packages) whose Spring Boot starter is
the only Boot-coupled surface. Staying on Boot 2.7 (OSS EOL) and Java 17 blocks
consumers who have moved to the current LTS platform. Boot 3.5.x supports Java
17–25, so 3.5.14 + Java 25 is a supported combination.

**The usual Boot 2→3 pain does not apply here:** the codebase contains **zero
`javax.*` references** (verified by grep across the reactor), so there is no
javax→jakarta namespace migration. The migration is therefore small and
well-bounded: version bumps, one dead resource file, and an auto-config idiom
refresh.

## Decisions (confirmed)

- **Strategy:** A — big-bang. All modules move in one coherent change.
- **Baseline scope:** single Java 25 baseline for **all four modules**
  (`doc-engine-api`, `doc-engine-core`, `doc-engine-jodconverter`,
  `doc-engine-spring-boot-starter`) via one `maven.compiler.release` property in
  the parent. Consumers must be on Java 25+.
- **Auto-config modernization:** full — delete `spring.factories`, migrate to
  `@AutoConfiguration`, drop the Boot-2.7 registration test.
- **Git:** perform on a **new branch from `master`**; the in-progress
  `fix/converter-resource-leaks` working-tree changes are preserved first.

## Scope

**In scope:**
- Parent `pom.xml`: compiler baseline → Java 25; Spring Boot 2.7.18 → 3.5.14;
  tooling bumps (below).
- Delete `doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories`.
- Migrate the two starter auto-configuration classes to `@AutoConfiguration`.
- Remove the now-invalid `registeredViaSpringFactoriesForBoot27` test.
- Verify a full `mvn clean verify` on JDK 25.

**Out of scope (YAGNI):**
- **javax→jakarta migration** — no `javax.*` usage exists.
- **Upgrading jxls/jxls-poi/commons-jexl3/slf4j** — kept as-is unless the Java 25
  build proves a concrete incompatibility (see Risks → contingency).
- **Upgrading Maven** — Maven 3.8.4 runs on JDK 25 (verified; only cosmetic jansi
  native-access warnings).
- **JPMS module descriptors, `doc-engine-bom`, multi-baseline builds.**

## Design

### 1. Parent `pom.xml` — versions

Replace `maven.compiler.source`/`maven.compiler.target` (both `17`) with a single
`maven.compiler.release` = `25`.

| Property / plugin | From | To | Reason |
|---|---|---|---|
| `maven.compiler.{source,target}` → `maven.compiler.release` | `17` | `25` | new baseline |
| `spring-boot.version` | `2.7.18` | `3.5.14` | target Boot |
| `junit.version` | `5.10.2` | `5.12.2` | Java 25-safe |
| `mockito.version` | `5.11.0` | `5.18.0` | native Java 25 (byte-buddy 1.17.x, no experimental flag) |
| `assertj.version` | `3.25.3` | `3.27.3` | Java 25-friendly |
| `jodconverter.version` | `4.4.9` | `4.4.11` | newer-JDK fixes |
| jacoco-maven-plugin | `0.8.12` | `0.8.14` | official Java 25 class-file support |
| maven-surefire-plugin | `3.2.5` | `3.5.6` | newer-JDK support |
| maven-failsafe-plugin | `3.2.5` | `3.5.6` | newer-JDK support |
| maven-compiler-plugin | `3.13.0` | `3.14.0` | newer-JDK support |

Unchanged: `jxls`/`jxls-poi` `2.13.0`, `commons-jexl3` `3.3`, `slf4j` `2.0.13`.

The Spring Boot artifacts consumed are `spring-boot-autoconfigure`,
`spring-boot-configuration-processor`, `spring-boot-test` — all move to 3.5.14 via
the single `spring-boot.version` property. They are `provided`/`optional`/`test`
scoped, so the library does not force a Boot runtime on plain-Java consumers.

### 2. Starter — auto-configuration modernization

- **Delete** `META-INF/spring.factories`. Boot 3 does not read
  `EnableAutoConfiguration=` from `spring.factories`; registration is via the
  already-present `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **`DocEngineAutoConfiguration`:** `@Configuration(proxyBeanMethods = false)` →
  `@AutoConfiguration`. All conditionals used (`@ConditionalOnMissingBean`,
  `@ConditionalOnProperty`, `@EnableConfigurationProperties`) exist unchanged in
  Boot 3.5.
- **`JodConverterAutoConfiguration`:** `@Configuration(proxyBeanMethods = false)` +
  `@AutoConfigureBefore(DocEngineAutoConfiguration.class)` →
  `@AutoConfiguration(before = DocEngineAutoConfiguration.class)`.
  `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`
  unchanged.

The `DocEngineProperties` record, `additional-spring-configuration-metadata.json`,
and the `AutoConfiguration.imports` file need **no** change.

### 3. Starter — test adjustments

`DocEngineAutoConfigurationTest`:
- **Remove** `registeredViaSpringFactoriesForBoot27()` and its now-unused imports
  (`EnableAutoConfiguration`, `SpringFactoriesLoader`). It asserted the Boot-2.7
  registration path we are deleting.
- **Keep** `registeredViaAutoConfigurationImportsForBoot3()` — `ImportCandidates`
  and the `.imports` file are the Boot 3 mechanism and remain valid.
- All other tests use `ApplicationContextRunner` / `AutoConfigurations` /
  `@ConditionalOn*`, unchanged in Boot 3.5.

### 4. Build & verification

- Build with the installed **Temurin JDK 25** at
  `C:\Users\Nikolay\.jdks\jdk-25.0.3+9` (verified: `javac 25.0.3`). Set
  `JAVA_HOME` to it for every Maven invocation. Maven 3.8.4 runs on JDK 25.
- **Done = green `mvn clean verify`** across all four modules: unit tests pass and
  the core JaCoCo branch-coverage floor (0.70) still holds (no core production
  code changes, so coverage is unaffected).

## Risks & contingencies

- **Apache POI on Java 25** (transitive via `jxls-poi` 2.13.0): POI is generally
  Java-25-clean, but if a reflection/module error surfaces, override POI to a
  current 5.x in the parent `dependencyManagement` (no jxls bump needed).
- **`jodconverter-local` on Java 25:** known JPMS/reflection issues on newer JDKs
  ("Unable to create instance DocumentFormat"). Bumping to 4.4.11 is the first
  mitigation; if it persists, add the required `--add-opens` to the surefire/
  failsafe `argLine` (jodconverter tests only). The jod module is optional, so
  this cannot break core.
- **Mockito self-attaching agent warning on Java 25:** Mockito 5.18 works, but the
  inline mock maker may emit a dynamic-agent-load warning. If it appears, wire the
  Mockito java-agent explicitly via a surefire `-javaagent` argLine. Warning, not
  a failure.

## Rollback

The change is confined to POM properties, two annotations, one deleted resource,
and one deleted test method. Reverting the migration branch (or not merging it)
fully restores the Java 17 + Boot 2.7 state.
