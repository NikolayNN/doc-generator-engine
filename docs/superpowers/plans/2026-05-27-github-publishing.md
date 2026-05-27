# GitHub Packages Publishing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable publishing `doc-engine-core` and `doc-engine-spring-boot-starter` to GitHub Packages via git tag `v*` through GitHub Actions.

**Architecture:** Parent POM gets publishing metadata (groupId `io.github.nikolaynn`, scm, license, distributionManagement). A `release` Maven profile adds source/javadoc JARs. Two GitHub Actions workflows: `build.yml` runs `mvn verify` on PRs; `release.yml` derives version from the tag and runs `mvn -Prelease deploy` against GitHub Packages.

**Tech Stack:** Maven 3, Java 17, GitHub Actions, GitHub Packages, `maven-source-plugin` 3.3.1, `maven-javadoc-plugin` 3.6.3.

**Spec:** [`docs/superpowers/specs/2026-05-27-github-publishing-design.md`](../specs/2026-05-27-github-publishing-design.md)

---

## File Map

| Path | Action | Responsibility |
|---|---|---|
| `pom.xml` | Modify | Change groupId, add public metadata (`<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>`), `<distributionManagement>`, plugin versions in `<pluginManagement>`, `release` profile. |
| `doc-engine-core/pom.xml` | Modify | Update `<parent><groupId>` only. |
| `doc-engine-spring-boot-starter/pom.xml` | Modify | Update `<parent><groupId>` and the internal dependency on `doc-engine-core` to the new groupId. |
| `LICENSE` | Create | Full text of Apache License 2.0. |
| `README.md` | Modify | Add «Installing from GitHub Packages» section; replace `com.example` in code examples with `io.github.nikolaynn`. |
| `.github/maven-settings.xml` | Create | Maven server `github` with placeholders `${env.GITHUB_ACTOR}` / `${env.GITHUB_TOKEN}`. |
| `.github/workflows/build.yml` | Create | CI on push to `master` and PRs — `mvn -B verify`. |
| `.github/workflows/release.yml` | Create | On tag `v*` — set version from tag, `mvn -Prelease deploy`, `gh release create`. |

---

## Task 1: Switch groupId and add public POM metadata

**Files:**
- Modify: `pom.xml`

This task changes `groupId` and adds metadata required by Maven publishing (`<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>`). It does NOT touch `<distributionManagement>` or the `release` profile yet — those are separate tasks so each commit is small and revertable.

- [ ] **Step 1: Edit parent pom — change `<groupId>` and add metadata block**

In `pom.xml`, change line 7 from `<groupId>com.example</groupId>` to:

```xml
<groupId>io.github.nikolaynn</groupId>
```

Then, right after the existing `<name>Document Generator Engine</name>` (line 11), insert this block:

```xml
    <description>Java library for generating documents from XLSX templates (JXLS) with optional PDF export via headless LibreOffice.</description>
    <url>https://github.com/NikolayNN/doc-generator-engine</url>

    <licenses>
        <license>
            <name>Apache License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
        </license>
    </licenses>

    <developers>
        <developer>
            <id>NikolayNN</id>
            <name>Nikolay</name>
            <email>nikolay.horushko@gmail.com</email>
            <url>https://github.com/NikolayNN</url>
        </developer>
    </developers>

    <scm>
        <connection>scm:git:https://github.com/NikolayNN/doc-generator-engine.git</connection>
        <developerConnection>scm:git:git@github.com:NikolayNN/doc-generator-engine.git</developerConnection>
        <url>https://github.com/NikolayNN/doc-generator-engine</url>
        <tag>HEAD</tag>
    </scm>
```

- [ ] **Step 2: Verify parent pom still parses**

Run:
```
mvn -N help:effective-pom -q -DforceStdout > /tmp/effective-parent.xml
```
Expected: command exits 0; `/tmp/effective-parent.xml` contains `<groupId>io.github.nikolaynn</groupId>` and the `<scm>` block.

- [ ] **Step 3: Commit**

```
git add pom.xml
git commit -m "build: rename groupId to io.github.nikolaynn and add publishing metadata"
```

---

## Task 2: Update child POMs to new parent groupId

**Files:**
- Modify: `doc-engine-core/pom.xml`
- Modify: `doc-engine-spring-boot-starter/pom.xml`

After Task 1 the children still reference `<parent><groupId>com.example</groupId>` — the build is currently broken. This task restores it.

- [ ] **Step 1: Verify the build is broken first** (sanity check)

Run:
```
mvn -B -DskipTests validate
```
Expected: FAIL with `Non-resolvable parent POM ... com.example:doc-generator-engine`.

- [ ] **Step 2: Edit `doc-engine-core/pom.xml`**

Change line 8 from `<groupId>com.example</groupId>` to:

```xml
        <groupId>io.github.nikolaynn</groupId>
```

(inside the existing `<parent>` block)

- [ ] **Step 3: Edit `doc-engine-spring-boot-starter/pom.xml`**

Two replacements in this file:

1. In the `<parent>` block (around line 8), change `<groupId>com.example</groupId>` to:

```xml
        <groupId>io.github.nikolaynn</groupId>
```

2. In the `<dependencies>` block (around line 18), change `<groupId>com.example</groupId>` (the one referencing `doc-engine-core`) to:

```xml
            <groupId>io.github.nikolaynn</groupId>
```

- [ ] **Step 4: Verify the full build passes**

Run:
```
mvn -B -DskipTests clean install
```
Expected: BUILD SUCCESS. All three modules build.

- [ ] **Step 5: Run unit tests**

Run:
```
mvn -B test
```
Expected: BUILD SUCCESS. `LibreOfficeConverterIT` and `EndToEndTest#pdfRoundTripWithBuilder` are skipped on machines without `soffice` (via `@EnabledIf`).

- [ ] **Step 6: Commit**

```
git add doc-engine-core/pom.xml doc-engine-spring-boot-starter/pom.xml
git commit -m "build: update child poms to new parent groupId"
```

---

## Task 3: Add LICENSE file

**Files:**
- Create: `LICENSE`

The `<licenses>` block in pom must be backed by an actual `LICENSE` file at repo root — otherwise GitHub does not auto-detect the license.

- [ ] **Step 1: Create `LICENSE`**

Create file `LICENSE` at repo root with the **full** Apache License 2.0 text from https://www.apache.org/licenses/LICENSE-2.0.txt.

The trailing copyright block at the very bottom of the file should read:

```
Copyright 2026 Nikolay

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

(Above this block is the full ~200-line license text; copy it verbatim from the URL above.)

- [ ] **Step 2: Verify file size is plausible**

Run:
```
wc -l LICENSE
```
Expected: at least 170 lines (the Apache 2.0 text is ~200 lines).

- [ ] **Step 3: Commit**

```
git add LICENSE
git commit -m "docs: add Apache License 2.0"
```

---

## Task 4: Add `release` profile with source and javadoc JARs

**Files:**
- Modify: `pom.xml`

The `release` profile activates `maven-source-plugin` and `maven-javadoc-plugin` only on deploy, so local `mvn install` stays fast.

- [ ] **Step 1: Add plugin versions to `<pluginManagement>`**

In `pom.xml`, inside the existing `<build><pluginManagement><plugins>` block (around lines 100-113), add two entries after the `maven-surefire-plugin` entry:

```xml
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-source-plugin</artifactId>
                    <version>3.3.1</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-javadoc-plugin</artifactId>
                    <version>3.6.3</version>
                </plugin>
```

- [ ] **Step 2: Add the `release` profile**

In `pom.xml`, immediately before the closing `</project>` tag, add:

```xml
    <profiles>
        <profile>
            <id>release</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-source-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>attach-sources</id>
                                <goals>
                                    <goal>jar-no-fork</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-javadoc-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>attach-javadocs</id>
                                <goals>
                                    <goal>jar</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <doclint>none</doclint>
                            <failOnError>false</failOnError>
                            <quiet>true</quiet>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
```

- [ ] **Step 3: Local test — verify `release` profile produces extra JARs**

Run:
```
mvn -B -DskipTests -Prelease clean package
```
Expected: BUILD SUCCESS.

Then verify the extra JARs were produced:
```
ls doc-engine-core/target/*.jar
ls doc-engine-spring-boot-starter/target/*.jar
```
Expected: for each module, three JARs exist — `*-0.1.0-SNAPSHOT.jar`, `*-0.1.0-SNAPSHOT-sources.jar`, `*-0.1.0-SNAPSHOT-javadoc.jar`.

- [ ] **Step 4: Verify default (non-release) profile is unchanged**

Run:
```
mvn -B -DskipTests clean package
ls doc-engine-core/target/*.jar
```
Expected: only the main JAR exists, no `-sources.jar` / `-javadoc.jar`.

- [ ] **Step 5: Commit**

```
git add pom.xml
git commit -m "build: add release profile producing sources and javadoc JARs"
```

---

## Task 5: Add `<distributionManagement>` for GitHub Packages

**Files:**
- Modify: `pom.xml`

This points `mvn deploy` at GitHub Packages. Both `<repository>` and `<snapshotRepository>` use the same URL and id (`github`) — GitHub Packages accepts both releases and snapshots at one endpoint.

- [ ] **Step 1: Add `<distributionManagement>` block**

In `pom.xml`, immediately after the closing `</dependencyManagement>` tag and before the `<build>` tag, add:

```xml
    <distributionManagement>
        <repository>
            <id>github</id>
            <name>GitHub Packages</name>
            <url>https://maven.pkg.github.com/NikolayNN/doc-generator-engine</url>
        </repository>
        <snapshotRepository>
            <id>github</id>
            <name>GitHub Packages</name>
            <url>https://maven.pkg.github.com/NikolayNN/doc-generator-engine</url>
        </snapshotRepository>
    </distributionManagement>
```

- [ ] **Step 2: Dry-run deploy to a local directory**

This verifies the publication wiring without contacting GitHub. We override the deployment target with `-DaltDeploymentRepository`:

```
mvn -B -Prelease -DskipTests clean deploy -DaltDeploymentRepository=local::default::file:./target/local-repo
```
Expected: BUILD SUCCESS.

Then verify the local repo received the three artifacts:
```
ls target/local-repo/io/github/nikolaynn/doc-engine-core/0.1.0-SNAPSHOT/
```
Expected: directory contains `doc-engine-core-0.1.0-SNAPSHOT.jar`, `*-sources.jar`, `*-javadoc.jar`, and `*.pom`.

- [ ] **Step 3: Clean up local dry-run output**

Run:
```
rm -rf target/local-repo
```

- [ ] **Step 4: Commit**

```
git add pom.xml
git commit -m "build: add GitHub Packages distributionManagement"
```

---

## Task 6: Add Maven settings.xml for CI

**Files:**
- Create: `.github/maven-settings.xml`

This settings file is **safe to commit**: it contains only placeholders, not secrets. CI env vars supply the actual credentials.

- [ ] **Step 1: Create directory if missing**

Run:
```
mkdir -p .github
```

- [ ] **Step 2: Create `.github/maven-settings.xml`**

Content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>${env.GITHUB_ACTOR}</username>
            <password>${env.GITHUB_TOKEN}</password>
        </server>
    </servers>
</settings>
```

The `<id>github</id>` matches the `<id>github</id>` inside `<distributionManagement>` (Task 5).

- [ ] **Step 3: Commit**

```
git add .github/maven-settings.xml
git commit -m "ci: add Maven settings.xml referencing GITHUB_ACTOR and GITHUB_TOKEN"
```

---

## Task 7: Add build workflow (CI on PRs and master pushes)

**Files:**
- Create: `.github/workflows/build.yml`

This is independent of publishing — it catches breakage between releases.

- [ ] **Step 1: Create directory if missing**

Run:
```
mkdir -p .github/workflows
```

- [ ] **Step 2: Create `.github/workflows/build.yml`**

Content:

```yaml
name: build

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Build and test
        run: mvn -B verify
```

`LibreOfficeConverterIT` is not picked up by maven-surefire-plugin (suffix `IT` doesn't match default include patterns) and the PDF e2e test is annotated `@EnabledIf("sofficeAvailable")` — so neither needs `soffice` on the runner.

- [ ] **Step 3: Local YAML syntax sanity check**

Run:
```
python -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"
```
Expected: no output (exit 0). If `python` is unavailable, skip — actual validation happens when GitHub parses the file on push.

- [ ] **Step 4: Commit**

```
git add .github/workflows/build.yml
git commit -m "ci: add build workflow for master pushes and PRs"
```

---

## Task 8: Add release workflow (publish on tag `v*`)

**Files:**
- Create: `.github/workflows/release.yml`

The workflow derives the Maven version from the tag (`v0.1.0` → `0.1.0`), rewrites the POM versions on the fly with `versions:set`, and runs `mvn -Prelease deploy`. POM in git stays at `0.1.0-SNAPSHOT` forever — versions live in tags.

- [ ] **Step 1: Create `.github/workflows/release.yml`**

Content:

```yaml
name: release

on:
  push:
    tags: ['v*']

permissions:
  contents: write
  packages: write

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Derive version from tag
        run: echo "RELEASE_VERSION=${GITHUB_REF_NAME#v}" >> "$GITHUB_ENV"

      - name: Set Maven project version
        run: mvn -B versions:set -DnewVersion=$RELEASE_VERSION -DgenerateBackupPoms=false

      - name: Deploy to GitHub Packages
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: mvn -B -Prelease deploy --settings .github/maven-settings.xml

      - name: Create GitHub Release
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: gh release create "$GITHUB_REF_NAME" --generate-notes
```

Notes:
- `GITHUB_ACTOR` is set automatically by the runner — no need to forward it explicitly.
- `versions:set -DgenerateBackupPoms=false` avoids leaving `.versionsBackup` files in the checkout (we never commit the rewrite back).
- `gh release create --generate-notes` builds release notes from commits/PRs since the previous tag.

- [ ] **Step 2: YAML syntax sanity check**

Run:
```
python -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"
```
Expected: no output (exit 0).

- [ ] **Step 3: Commit**

```
git add .github/workflows/release.yml
git commit -m "ci: add release workflow publishing to GitHub Packages on tag v*"
```

---

## Task 9: Update README with new groupId and «Installation» section

**Files:**
- Modify: `README.md`

Existing README has Maven snippets pointing at `com.example` and lacks any consumer-facing installation instructions for GitHub Packages.

- [ ] **Step 1: Replace `com.example` in README Maven snippet**

In `README.md`, find the snippet inside the «Быстрый старт — Spring Boot» section:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>doc-engine-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Replace with:

```xml
<dependency>
    <groupId>io.github.nikolaynn</groupId>
    <artifactId>doc-engine-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

- [ ] **Step 2: Add «Установка» section above «Быстрый старт — plain Java»**

Insert this section between «## Требования» and «## Быстрый старт — plain Java»:

```markdown
## Установка из GitHub Packages

Артефакты публикуются в GitHub Packages. Для **публичных** пакетов GitHub всё равно требует аутентификации потребителя — это известное ограничение GitHub Packages, обойти его без переезда на Maven Central нельзя.

### 1. Personal Access Token

Создай classic PAT в GitHub с правом `read:packages` (или fine-grained token с доступом «Packages: Read»).

### 2. `~/.m2/settings.xml`

​```xml
<settings>
    <servers>
        <server>
            <id>github-nikolaynn</id>
            <username>ВАШ_GITHUB_LOGIN</username>
            <password>ghp_xxxxxxxxxxxxxxxxxxxxxxxx</password>
        </server>
    </servers>
</settings>
​```

### 3. `pom.xml` потребителя

​```xml
<repositories>
    <repository>
        <id>github-nikolaynn</id>
        <url>https://maven.pkg.github.com/NikolayNN/doc-generator-engine</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.nikolaynn</groupId>
        <artifactId>doc-engine-spring-boot-starter</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
​```

`id` репозитория в `<repositories>` должен совпадать с `id` сервера в `settings.xml`.
```

(Note: the backticks shown as `​```` in the steps above use a zero-width space to render in this plan. When inserting into `README.md`, use plain triple backticks.)

- [ ] **Step 3: Commit**

```
git add README.md
git commit -m "docs: update README with new groupId and GitHub Packages install guide"
```

---

## Task 10: Final integration check

**Files:** none

Smoke check that the repo state is coherent before the first release tag.

- [ ] **Step 1: Full clean build + tests**

Run:
```
mvn -B clean verify
```
Expected: BUILD SUCCESS. All unit tests pass.

- [ ] **Step 2: Full release profile build**

Run:
```
mvn -B -Prelease -DskipTests clean package
```
Expected: BUILD SUCCESS. Each module's `target/` contains main JAR + `-sources.jar` + `-javadoc.jar`.

- [ ] **Step 3: Verify no `com.example` references remain in code or build files**

Run:
```
grep -r "com\.example" --include="pom.xml" --include="*.md" .
```
Expected: only design docs under `docs/superpowers/specs/` may still contain `com.example` (historic spec snapshot — that's fine). No matches in `pom.xml` files or `README.md`.

- [ ] **Step 4: Check git log is clean**

Run:
```
git log --oneline -10
```
Expected: visible commits from Tasks 1, 2, 3, 4, 5, 6, 7, 8, 9 in order.

- [ ] **Step 5: Done**

To trigger the first release after this plan is merged to GitHub:

```
git tag v0.1.0
git push origin v0.1.0
```

`release.yml` will publish `io.github.nikolaynn:{doc-generator-engine,doc-engine-core,doc-engine-spring-boot-starter}:0.1.0` to GitHub Packages and create a GitHub Release.

---

## Self-Review

Coverage check against spec:
- Spec §3.1 file structure → Tasks 1–9 cover every file in the table.
- Spec §3.2 parent pom metadata → Task 1.
- Spec §3.2 distributionManagement → Task 5.
- Spec §3.2 release profile → Task 4.
- Spec §3.3 maven-settings.xml → Task 6.
- Spec §3.4 release.yml → Task 8.
- Spec §3.5 build.yml → Task 7.
- Spec §3.6 LICENSE → Task 3.
- Spec §3.7 README update → Task 9.
- Spec §7 testing approach (local `-Prelease package` and `altDeploymentRepository` dry-run) → Tasks 4 step 3, 5 step 2.

No placeholders; every code change has the exact insertion text. No spec requirements unmapped.
