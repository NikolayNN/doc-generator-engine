# Harness Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the CI/release automation (timeouts, concurrency, re-runnable changelog-gated releases), make the JDK-25 requirement fail loudly via maven-enforcer, and capture build/test/release knowledge in CLAUDE.md.

**Architecture:** Four independent config-level tasks: two GitHub Actions workflow edits, one root-pom plugin addition, one new documentation file. No production code changes; each task is separately committable and revertible.

**Tech Stack:** GitHub Actions, Maven 3.8.4+ / JDK 25, maven-enforcer-plugin 3.5.0, `gh` CLI in workflows.

## Global Constraints

- Repo root: `C:\Users\Nikolay\IdeaProjects\doc-generator-engine`, branch `master` (project convention: commit directly to master).
- Any local `mvn` invocation MUST be prefixed with `export JAVA_HOME='C:\Users\Nikolay\.jdks\jdk-25.0.3+9'` (Git Bash) — the system JDK is older and fails with `invalid target release: 25`.
- Local Maven is 3.8.4 — the enforcer Maven floor must be `[3.8.4,)`, NOT 3.9+.
- YAML syntax check command available on this machine: `npx --yes js-yaml <file>` (prints the parsed document and exits 0 on valid YAML).
- Commit messages follow the repo's conventional style (`ci:`, `build:`, `docs:`) and end with the trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Do not reformat untouched parts of edited files; keep existing comments (e.g. the Windows-paths and release-gate comments in the workflows).

---

### Task 1: build.yml — job timeouts + concurrency cancellation

**Files:**
- Modify: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing other tasks rely on (independent).

- [ ] **Step 1: Add a workflow-level `concurrency` block after the `on:` block**

The file currently starts:

```yaml
name: build

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

jobs:
```

Insert between `on:` and `jobs:` so it reads:

```yaml
name: build

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

# a newer push to the same PR/branch supersedes the in-flight build; master
# builds are never cancelled so every commit keeps a full status
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.ref != 'refs/heads/master' }}

jobs:
```

- [ ] **Step 2: Add `timeout-minutes: 20` to each of the three jobs**

For each of `verify`, `windows`, `libreoffice-it`, add the line directly under `runs-on`. Example for `verify` (repeat the same one-line addition for the other two jobs):

```yaml
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
```

The LibreOffice ITs are the hang-prone part (orphaned soffice processes); a normal full run is well under 10 minutes, so 20 is generous without allowing a 6-hour default-timeout burn.

- [ ] **Step 3: Validate YAML syntax**

Run: `npx --yes js-yaml .github/workflows/build.yml`
Expected: parsed document printed as JSON-ish output, exit code 0, no `YAMLException`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "$(cat <<'EOF'
ci: add job timeouts and superseded-run cancellation to build workflow

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: release.yml — changelog gate + draft→deploy→publish release

**Files:**
- Modify: `.github/workflows/release.yml` (full rewrite, content below)

**Interfaces:**
- Consumes: `changelog/<version>.md` naming convention (already established: `changelog/0.1.0.md`).
- Produces: the release contract documented by Task 4's CLAUDE.md — tag `v*` requires `changelog/<version>.md` to exist; its content becomes the GitHub Release notes.

**Why this shape:** GitHub Packages rejects re-deploying an existing version, so `mvn deploy` is the one non-repeatable step. Creating the release as a draft *before* deploy means a failed deploy leaves only an invisible draft; `view || create` makes the create step idempotent so a re-run of the workflow doesn't die there. The only awkward failure left is "deploy succeeded, publish-edit failed", which is fixed by hand with one click or `gh release edit vX.Y.Z --draft=false` — acceptable for a one-API-call step. The changelog gate runs before any expensive setup so a forgotten changelog fails in seconds.

- [ ] **Step 1: Replace the file content**

Write `.github/workflows/release.yml` with exactly:

```yaml
name: release

on:
  push:
    tags: ['v*']

permissions:
  contents: write
  packages: write

# one release at a time; never cancel a deploy mid-flight
concurrency:
  group: release

jobs:
  publish:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Derive version from tag
        run: echo "RELEASE_VERSION=${GITHUB_REF_NAME#v}" >> "$GITHUB_ENV"

      # fail fast: every release ships with a curated changelog entry, which
      # also becomes the GitHub Release notes below
      - name: Require changelog entry
        run: |
          if [ ! -f "changelog/${RELEASE_VERSION}.md" ]; then
            echo "::error::changelog/${RELEASE_VERSION}.md is missing — write it before tagging (see changelog/README.md)"
            exit 1
          fi

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      # release gate: with soffice present the LibreOffice conversion ITs run
      # during deploy, so a tag cannot ship with a broken PDF path
      - name: Install LibreOffice
        run: |
          sudo apt-get update
          sudo apt-get install -y libreoffice-calc
      - name: Assert soffice is available
        run: soffice --version

      - name: Set Maven project version
        run: mvn -B versions:set -DnewVersion=$RELEASE_VERSION -DgenerateBackupPoms=false

      # draft first: GitHub Packages rejects re-deploying the same version, so
      # nothing repeatable may come after deploy. The draft stays invisible
      # until published below; `view ||` keeps workflow re-runs idempotent.
      - name: Create draft GitHub Release
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1 || \
            gh release create "$GITHUB_REF_NAME" --verify-tag --draft \
              --title "$GITHUB_REF_NAME" --notes-file "changelog/${RELEASE_VERSION}.md"

      - name: Deploy to GitHub Packages
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: mvn -B -Prelease deploy --settings .github/maven-settings.xml

      - name: Publish GitHub Release
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: gh release edit "$GITHUB_REF_NAME" --draft=false
```

- [ ] **Step 2: Validate YAML syntax**

Run: `npx --yes js-yaml .github/workflows/release.yml`
Expected: parsed document printed, exit code 0.

- [ ] **Step 3: Sanity-check the gate logic locally**

Run (Git Bash, from repo root):

```bash
RELEASE_VERSION=0.1.0; [ -f "changelog/${RELEASE_VERSION}.md" ] && echo GATE-PASS
RELEASE_VERSION=9.9.9; [ -f "changelog/${RELEASE_VERSION}.md" ] || echo GATE-FAIL-AS-EXPECTED
```

Expected output: `GATE-PASS` then `GATE-FAIL-AS-EXPECTED`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "$(cat <<'EOF'
ci: gate releases on changelog entry, publish via pre-deploy draft release

The changelog file becomes the release notes (replaces --generate-notes);
the draft-before-deploy ordering keeps failed runs re-runnable since
GitHub Packages rejects re-deploying an existing version.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: maven-enforcer-plugin — JDK 25 / Maven floor / duplicate-dep ban

**Files:**
- Modify: `pom.xml` (root; `<build><pluginManagement>` and `<build><plugins>`)

**Interfaces:**
- Consumes: nothing.
- Produces: `mvn validate` now fails with an explicit `requireJavaVersion` message when Maven runs on a JDK other than 25 — Task 4's CLAUDE.md references this behavior.

- [ ] **Step 1: Pin the plugin version in `pluginManagement`**

In root `pom.xml`, inside `<build><pluginManagement><plugins>`, after the `maven-compiler-plugin` entry, add:

```xml
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-enforcer-plugin</artifactId>
                    <version>3.5.0</version>
                </plugin>
```

(3.5.0 is a known-resolvable stable version; Dependabot will propose newer ones.)

- [ ] **Step 2: Add the enforcer execution in `<build><plugins>`**

In root `pom.xml`, inside `<build><plugins>`, before the `maven-failsafe-plugin` entry, add:

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-environment</id>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <!-- fail with a clear message instead of javac's
                                     "invalid target release: 25" when Maven runs
                                     on an older JDK -->
                                <requireJavaVersion>
                                    <version>[25,26)</version>
                                </requireJavaVersion>
                                <requireMavenVersion>
                                    <version>[3.8.4,)</version>
                                </requireMavenVersion>
                                <banDuplicatePomDependencyVersions/>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```

- [ ] **Step 3: Red — verify the enforcer fails loudly on the system JDK**

Run (Git Bash, from repo root, deliberately WITHOUT the JDK-25 override):

```bash
env -u JAVA_HOME mvn -B validate
```

Expected: `BUILD FAILURE` whose error text names `requireJavaVersion` / "Detected JDK version ... is not in the allowed range [25,26)". It must NOT be javac's `invalid target release: 25` (that would mean the enforcer isn't running at validate).

Note: if the machine's PATH `java` happens to be JDK 25 this step passes instead — then just confirm the rule via `mvn -B validate -Denforcer.skip=false` under the old JDK from `C:\Program Files\...` if one exists, or accept the green path and move on; the rule content is declarative.

- [ ] **Step 4: Green — verify the full build still passes under JDK 25**

Run:

```bash
export JAVA_HOME='C:\Users\Nikolay\.jdks\jdk-25.0.3+9'
mvn -B verify
```

Expected: `BUILD SUCCESS` for all four modules (LibreOffice-gated ITs will skip locally — that is normal). If `banDuplicatePomDependencyVersions` reports a duplicate in any module pom, fix that duplicate dependency declaration rather than removing the rule.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "$(cat <<'EOF'
build: enforce JDK 25 and Maven 3.8.4 floor via maven-enforcer-plugin

Replaces javac's cryptic "invalid target release: 25" with an explicit
requireJavaVersion failure at validate; also bans duplicate pom deps.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: CLAUDE.md — build/test/release knowledge for agents and contributors

**Files:**
- Create: `CLAUDE.md` (repo root)

**Interfaces:**
- Consumes: the release contract from Task 2 (changelog gate, draft flow) and the enforcer behavior from Task 3 — execute this task last so the text below is accurate when committed.
- Produces: nothing (documentation leaf).

- [ ] **Step 1: Create `CLAUDE.md` with exactly this content**

```markdown
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
- Полная сборка с тестами: `mvn -B verify`
- В PowerShell goal-аргументы вида `-Dkey=value` местами съедаются парсером —
  используйте stop-parsing: `mvn --% -B versions:set -DnewVersion=0.3.0`.

## Модули

| Модуль | Роль |
|---|---|
| `doc-engine-api` | публичный API + SPI, без сторонних зависимостей |
| `doc-engine-core` | реализации: JXLS-движок, LibreOffice-конвертер, `DocumentEngineBuilder` |
| `doc-engine-spring-boot-starter` | автоконфигурация Spring Boot 3.x поверх core |
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
```

- [ ] **Step 2: Proofread against reality**

Check each factual claim resolves: `changelog/README.md` exists; `SampleTemplateGenerator` uses `regenerate.samples`; starter is Spring Boot 3.x (root pom `spring-boot.version` is 3.5.x); release workflow steps match Task 2's file. Fix any drift.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs: add CLAUDE.md with build, test-gating and release conventions

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```
