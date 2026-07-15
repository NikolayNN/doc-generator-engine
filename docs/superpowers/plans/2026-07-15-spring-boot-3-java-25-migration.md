# Spring Boot 3.5.14 + Java 25 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the whole `doc-generator-engine` reactor from Java 17 + Spring Boot 2.7.18 to Java 25 + Spring Boot 3.5.14, keeping every module green.

**Architecture:** The project is a Maven multi-module library; Spring Boot only touches the starter module (as `provided`/`optional`/`test`). The migration is version bumps + a Boot-3 auto-config idiom refresh. There is **no** `javax`→`jakarta` work — the codebase has zero `javax.*` references. The starter's `@AutoConfiguration` + `AutoConfiguration.imports` mechanism already works on Boot 2.7, so the idiom refresh (Task 1) is verified green on the current JDK 17 baseline *before* the platform jump (Task 2), isolating "did the refactor break wiring?" from "does the platform jump work?".

**Tech Stack:** Java 25 (Temurin), Maven 3.8.4, Spring Boot 3.5.14 (autoconfigure/configuration-processor/test), JXLS 2.13 + Apache POI, JODConverter 4.4.11, JUnit 5.12.2 + Mockito 5.18.0 + AssertJ 3.27.3, JaCoCo 0.8.14.

## Global Constraints

- **Branch:** all work lands on `build/spring-boot-3-java-25` (already created from `master`).
- **Build JDK is Java 25 for Task 2 onward:** Temurin at `C:\Users\Nikolay\.jdks\jdk-25.0.3+9`. Set `JAVA_HOME` for every `mvn` call — the machine's default `JAVA_HOME` points at Corretto 11 and will NOT build. Task 1 is verified on JDK 17 at `C:\Program Files\Java\jdk-17`.
- **Maven:** `mvn` on PATH is Apache Maven 3.8.4; it runs on JDK 25 (only cosmetic jansi native-access warnings). No Maven upgrade.
- **Single Java baseline:** `maven.compiler.release=25` in the parent, inherited by all four modules. No per-module baseline.
- **Exact target versions (copy verbatim):** `spring-boot.version=3.5.14`, `junit.version=5.12.2`, `mockito.version=5.18.0`, `assertj.version=3.27.3`, `jodconverter.version=4.4.11`, jacoco-maven-plugin `0.8.14`, maven-surefire-plugin `3.5.6`, maven-failsafe-plugin `3.5.6`, maven-compiler-plugin `3.14.0`. Unchanged: `jxls`/`jxls-poi` `2.13.0`, `commons-jexl3` `3.3`, `slf4j` `2.0.13`.
- **Coverage gate stays:** `doc-engine-core` JaCoCo BRANCH floor `0.70` must still pass (no core production code changes, so it should).
- **Integration tests (`*IT`) are environment-gated:** `LibreOfficeConverterIT` / `JodDocumentConverterIT` skip when no real `soffice` is installed locally — that is expected and not a failure.

---

## Execution note (as shipped)

Two version details differ from the plan text above, decided during execution and
verified green on JDK 25:
- **Mockito is `5.23.0` (not `5.18.0`), with `net.bytebuddy:byte-buddy` +
  `byte-buddy-agent` pinned to `1.18.11`** in the parent `dependencyManagement` —
  byte-buddy formally supports Java 25 only from `1.18.9`, and both Mockito 5.18
  and 5.23 bundle an older byte-buddy, so the explicit pin is required to avoid
  the experimental flag. Follow-up: drop the pin once a Mockito bundles
  byte-buddy `>= 1.18.9`.
- **The starter POM gained `annotationProcessorPaths` (for
  `spring-boot-configuration-processor`) + a surefire `argLine`**
  (`@{argLine} -XX:+EnableDynamicAgentLoading -Xshare:off`), because a JDK-25
  Maven host no longer auto-discovers classpath annotation processors. This
  extends Task 2 beyond "parent-pom-only," using Spring Boot's standard pattern.

---

## File Structure

- `pom.xml` (parent) — all version properties and plugin versions. **Task 2.**
- `doc-engine-spring-boot-starter/src/main/java/.../starter/DocEngineAutoConfiguration.java` — `@Configuration`→`@AutoConfiguration`. **Task 1.**
- `doc-engine-spring-boot-starter/src/main/java/.../starter/JodConverterAutoConfiguration.java` — `@Configuration`+`@AutoConfigureBefore`→`@AutoConfiguration(before=…)`. **Task 1.**
- `doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories` — deleted (dead in Boot 3). **Task 1.**
- `doc-engine-spring-boot-starter/src/test/java/.../starter/DocEngineAutoConfigurationTest.java` — remove the Boot-2.7 registration test. **Task 1.**
- `.github/workflows/build.yml`, `.github/workflows/release.yml` — bump `java-version` 17→25. **Task 3.**
- `README.md` — requirement line + starter description. **Task 3.**

---

## Task 1: Modernize the starter auto-config to the Boot 3 idiom (verified on JDK 17)

This refactor is Boot-2.7-compatible, so it is proven green on the *current* platform before anything else changes. The regression guard is the existing `registeredViaAutoConfigurationImportsForBoot3` test plus every bean-presence test in `DocEngineAutoConfigurationTest` (they use `AutoConfigurations.of(...)`, which does not depend on `spring.factories`).

**Files:**
- Modify: `doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/DocEngineAutoConfiguration.java`
- Modify: `doc-engine-spring-boot-starter/src/main/java/io/github/nikolaynn/docengine/starter/JodConverterAutoConfiguration.java`
- Delete: `doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories`
- Test: `doc-engine-spring-boot-starter/src/test/java/io/github/nikolaynn/docengine/starter/DocEngineAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `org.springframework.boot.autoconfigure.AutoConfiguration` (annotation, present since Boot 2.7.0), `AutoConfiguration#before`.
- Produces: two auto-configuration classes registered solely via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (unchanged file). Bean names and conditions are unchanged, so no other task depends on new symbols.

- [ ] **Step 1: Convert `DocEngineAutoConfiguration` to `@AutoConfiguration`**

In `DocEngineAutoConfiguration.java`, replace the import
`import org.springframework.context.annotation.Configuration;`
with
`import org.springframework.boot.autoconfigure.AutoConfiguration;`
and change the class annotation from:

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocEngineProperties.class)
public class DocEngineAutoConfiguration {
```

to:

```java
@AutoConfiguration
@EnableConfigurationProperties(DocEngineProperties.class)
public class DocEngineAutoConfiguration {
```

Leave `import org.springframework.context.annotation.Bean;` in place — `@Bean` is still used.

- [ ] **Step 2: Convert `JodConverterAutoConfiguration` to `@AutoConfiguration(before = …)`**

In `JodConverterAutoConfiguration.java`, remove these two imports:

```java
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Configuration;
```

add:

```java
import org.springframework.boot.autoconfigure.AutoConfiguration;
```

and change the class annotations from:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JodDocumentConverter.class)
@AutoConfigureBefore(DocEngineAutoConfiguration.class)
@EnableConfigurationProperties(DocEngineProperties.class)
public class JodConverterAutoConfiguration {
```

to:

```java
@AutoConfiguration(before = DocEngineAutoConfiguration.class)
@ConditionalOnClass(JodDocumentConverter.class)
@EnableConfigurationProperties(DocEngineProperties.class)
public class JodConverterAutoConfiguration {
```

Leave `import org.springframework.context.annotation.Bean;` in place.

- [ ] **Step 3: Delete the dead `spring.factories`**

```bash
git rm doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories
```

- [ ] **Step 4: Remove the Boot-2.7 registration test and its now-unused imports**

In `DocEngineAutoConfigurationTest.java`, delete these two imports:

```java
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.io.support.SpringFactoriesLoader;
```

and delete the entire test method:

```java
    @Test
    void registeredViaSpringFactoriesForBoot27() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(
                EnableAutoConfiguration.class, getClass().getClassLoader()))
            .contains(DocEngineAutoConfiguration.class.getName());
    }
```

Keep `import org.springframework.boot.autoconfigure.AutoConfiguration;` — it is still used by `registeredViaAutoConfigurationImportsForBoot3()` (`ImportCandidates.load(AutoConfiguration.class, …)`).

- [ ] **Step 5: Run the starter tests on JDK 17 to verify green**

Run:

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-17"
mvn -pl doc-engine-spring-boot-starter -am test
```

Expected: `BUILD SUCCESS`. `DocEngineAutoConfigurationTest` runs 13 tests (one fewer than before — the Boot-2.7 test is gone), all passing, including `registeredViaAutoConfigurationImportsForBoot3`. `ConfigMetadataTest` also passes.

- [ ] **Step 6: Commit**

```bash
git add doc-engine-spring-boot-starter
git commit -m "refactor: modernize starter auto-config to the Boot 3 idiom

Switch both auto-configuration classes from @Configuration(proxyBeanMethods=false)
to @AutoConfiguration (JodConverterAutoConfiguration uses before=... instead of
@AutoConfigureBefore), and delete the dead META-INF/spring.factories — Boot 3 reads
auto-configurations only from AutoConfiguration.imports. Drop the Boot-2.7-only
registration test (SpringFactoriesLoader.loadFactoryNames is removed in Spring 6).
Verified green on Boot 2.7.18 + JDK 17; both mechanisms have coexisted since 2.7.0.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Bump the platform to Java 25 + Spring Boot 3.5.14 (verified on JDK 25)

This is the atomic platform jump: one edit to the parent `pom.xml`, then a full-reactor verify on JDK 25.

**Files:**
- Modify: `pom.xml` (parent) — `<properties>` and `<build><pluginManagement>` versions.

**Interfaces:**
- Consumes: nothing new from Task 1 beyond a starter module that already compiles without `SpringFactoriesLoader.loadFactoryNames` (removed in Spring 6 / Boot 3).
- Produces: a Java-25 baseline and Boot 3.5.14 across the reactor. No new code symbols.

- [ ] **Step 1: Replace the compiler baseline with `release=25`**

In `pom.xml`, in `<properties>`, replace these two lines:

```xml
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
```

with a single line:

```xml
        <maven.compiler.release>25</maven.compiler.release>
```

- [ ] **Step 2: Bump the dependency version properties**

In the same `<properties>` block, apply these exact changes:

```xml
        <spring-boot.version>3.5.14</spring-boot.version>
        <jodconverter.version>4.4.11</jodconverter.version>
        <junit.version>5.12.2</junit.version>
        <mockito.version>5.18.0</mockito.version>
        <assertj.version>3.27.3</assertj.version>
```

(Was: `spring-boot.version` 2.7.18, `jodconverter.version` 4.4.9, `junit.version` 5.10.2, `mockito.version` 5.11.0, `assertj.version` 3.25.3. Leave `jxls.version`, `jxls-poi.version`, `commons-jexl3.version`, `slf4j.version` untouched.)

- [ ] **Step 3: Bump the plugin versions in `<pluginManagement>`**

In `pom.xml` under `<build><pluginManagement><plugins>`, change the versions:

```xml
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.14.0</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.6</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.5.6</version>
                </plugin>
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>0.8.14</version>
                </plugin>
```

(Was: compiler 3.13.0, surefire 3.2.5, failsafe 3.2.5, jacoco 0.8.12. Leave maven-source-plugin and maven-javadoc-plugin versions as-is.)

- [ ] **Step 4: Full-reactor verify on JDK 25**

Run:

```bash
export JAVA_HOME="C:/Users/Nikolay/.jdks/jdk-25.0.3+9"
mvn clean verify
```

Expected: `BUILD SUCCESS` for all four modules. Reference test counts from the pre-migration green baseline: api 24, core 71 (1 `*IT` skipped), jodconverter 12 (2 `*IT` skipped), starter 13 (after Task 1). `jacoco-maven-plugin:0.8.14:check` reports "All coverage checks have been met." Cosmetic noise that is NOT failure: `ERROR StatusLogger Log4j2 could not find a logging implementation` (POI/log4j2-api has no binding on the test classpath), jansi native-access warnings, and `Sharing is only supported…` HotSpot warnings.

- [ ] **Step 5: (Contingency — only if Step 4 failed) apply the matching fix, then re-run Step 4**

Diagnose by the symptom and apply the specific fix below; each is self-contained. After applying one, re-run the Step 4 command.

- **Symptom: `mockito`/`byte-buddy` error mentioning an unsupported class file version, or a hard failure about dynamic agent loading.** Configure the Mockito agent explicitly via surefire. In `pom.xml` `<pluginManagement>`, add configuration to the surefire plugin:

  ```xml
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.6</version>
                    <configuration>
                        <argLine>-XX:+EnableDynamicAgentLoading -Xshare:off</argLine>
                    </configuration>
                </plugin>
  ```

  Note: if JaCoCo's `prepare-agent` already sets `argLine`, use `@{argLine}` to preserve it: `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>`.

- **Symptom: Apache POI throws `InaccessibleObjectException` / `IllegalAccessError` / module access errors during `doc-engine-core` tests (JXLS rendering).** Pin a Java-25-current POI in the parent `<dependencyManagement>` (jxls stays 2.13.0):

  ```xml
            <dependency>
                <groupId>org.apache.poi</groupId>
                <artifactId>poi</artifactId>
                <version>5.4.1</version>
            </dependency>
            <dependency>
                <groupId>org.apache.poi</groupId>
                <artifactId>poi-ooxml</artifactId>
                <version>5.4.1</version>
            </dependency>
  ```

- **Symptom: `jodconverter` fails building the `DocumentFormat` registry (e.g. `Unable to create instance DocumentFormat`, or a reflection/`--add-opens` error) — most likely in `JodDocumentConverterTest.buildFailureMapsToConversionException`.** Add opens to the surefire argLine for the reactor (harmless to other modules):

  ```xml
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.6</version>
                    <configuration>
                        <argLine>@{argLine} --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED</argLine>
                    </configuration>
                </plugin>
  ```

  If this reveals a deeper jodconverter incompatibility that a version bump/opens cannot fix, STOP and report — the design's fallback is to document a required consumer JVM flag rather than force a broken build. Do not silently disable the jodconverter tests.

- [ ] **Step 6: Commit**

```bash
git add pom.xml
git commit -m "build: migrate the reactor to Java 25 + Spring Boot 3.5.14

Set maven.compiler.release=25 (single baseline, all modules), bump Spring Boot
2.7.18 -> 3.5.14, and bump every tool that would break on Java 25 class files:
JaCoCo 0.8.14, Mockito 5.18.0 (native Java 25, no experimental byte-buddy flag),
JUnit 5.12.2, surefire/failsafe 3.5.6, compiler 3.14.0; also AssertJ 3.27.3 and
JODConverter 4.4.11. Full mvn verify green on Temurin JDK 25.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

If any contingency from Step 5 was applied, extend the commit body with a one-line note of the fix (e.g. "surefire --add-opens for jodconverter's DocumentFormat registry on Java 25").

---

## Task 3: Update CI workflows and README to the new baseline

Docs/infra only — no local build. CI is validated by the push itself; here we only make the edits correct and consistent.

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`

**Interfaces:** none (no code symbols).

- [ ] **Step 1: Bump both JDK setups in `build.yml` to Java 25**

`.github/workflows/build.yml` has **two** jobs (`verify` and `libreoffice-it`), each with a `Set up JDK 17` step. In both, change:

```yaml
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
```

to:

```yaml
      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
```

- [ ] **Step 2: Bump the JDK setup in `release.yml` to Java 25**

In `.github/workflows/release.yml`, apply the same change to its single `Set up JDK 17` step (name → `Set up JDK 25`, `java-version: '17'` → `'25'`).

- [ ] **Step 3: Update the README requirement and starter description**

In `README.md`:

- Line 18, change `- Java 17+` to `- Java 25+`.
- Line 13, change the starter row from
  `| \`doc-engine-spring-boot-starter\` | Spring Boot 2.7 auto-configuration (совместима с 3.x). Тонкая обёртка поверх core. |`
  to
  `| \`doc-engine-spring-boot-starter\` | Spring Boot 3.x auto-configuration (\`@AutoConfiguration\` + \`AutoConfiguration.imports\`). Тонкая обёртка поверх core. |`

- [ ] **Step 4: Sanity-check the YAML edits**

Run:

```bash
git diff -- .github/workflows/build.yml .github/workflows/release.yml
```

Expected: exactly three `java-version` lines now read `'25'` (two in `build.yml`, one in `release.yml`), each with its `name:` updated to `Set up JDK 25`, and no other structural changes.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/build.yml .github/workflows/release.yml README.md
git commit -m "ci,docs: target Java 25 in CI and update README baseline

Bump setup-java to Temurin 25 in the build (both jobs) and release workflows,
and update the README requirement (Java 25+) and the starter's Boot-version note.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Post-plan follow-ups (not code tasks)

- Update the memory `build-needs-jdk17-java-home.md`: after this migration the build requires **JDK 25** (`C:\Users\Nikolay\.jdks\jdk-25.0.3+9`), not JDK 17. Do this once Task 2 is verified green.
- `build.log` in the repo root is an untracked build artifact (not in `.gitignore`). Optional: add `*.log` to `.gitignore` on this branch, or delete the file. Not required for the migration.
- Pushing `master` (1 commit ahead of `origin/master` from the resource-leak merge) and the migration branch is left to the user.

## Self-Review

**Spec coverage** — every spec item maps to a task:
- Parent pom: compiler→25, spring-boot→3.5.14, tooling bumps → Task 2 (Steps 1–3).
- Delete `spring.factories` → Task 1 (Step 3).
- `@AutoConfiguration` migration (both classes, `before=`) → Task 1 (Steps 1–2).
- Remove `registeredViaSpringFactoriesForBoot27` → Task 1 (Step 4).
- Full `mvn clean verify` on JDK 25 + coverage gate → Task 2 (Step 4).
- Risks/contingencies (POI, jodconverter, Mockito agent) → Task 2 (Step 5), with concrete fixes.
- Out-of-scope (javax→jakarta, jxls/POI/Maven upgrades) → honored; POI override is contingency-only. CI/README were not in the spec's explicit file list but are required for the migration to hold in CI and are covered by Task 3.

**Placeholder scan:** no TBD/TODO/"handle errors" — every code step shows the exact edit; the contingency step gives concrete XML, not "add error handling."

**Type consistency:** annotation names (`@AutoConfiguration`, `AutoConfiguration#before`), plugin coordinates, and property names match across tasks and the current POM. No cross-task symbol references beyond the unchanged bean wiring.
