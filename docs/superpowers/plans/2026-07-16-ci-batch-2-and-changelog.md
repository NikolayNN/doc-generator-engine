# CI Batch 2 + Changelog 0.2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Second batch of CI hardening (job rename, manual trigger, SHA-pinned actions, Dependabot grouping, dependency review) plus the 0.2.0 changelog entry required by the new release gate.

**Architecture:** Config-only edits to the two workflows and dependabot.yml, plus one documentation deliverable (changelog) produced via the `writing-changelog` skill. Independent tasks, one commit each, executed on a `harness-batch-2` branch, fast-forward merged to master, pushed, CI watched.

**Tech Stack:** GitHub Actions, Dependabot v2 config, Keep a Changelog (russian, per `changelog/README.md`).

## Global Constraints

- Verified action SHAs (fetched live 2026-07-16 via `git ls-remote`, comment must name the version):
  - `actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1`
  - `actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9 # v4.8.0`
  - `actions/dependency-review-action@2031cfc080254a8a887f58cffee85186f0e49e48 # v4.9.0`
- Every `uses:` in both workflows gets pinned; Dependabot's github-actions ecosystem understands SHA pins with version comments and keeps them fresh.
- Renaming the `verify` job changes its status-check name; the repo has no branch-protection contexts relying on it (single-dev repo) — acceptable.
- YAML check: `npx --yes js-yaml <file>` exits 0.
- Commits end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: build.yml — rename no-soffice job, add workflow_dispatch

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Add `workflow_dispatch` to the trigger block**

```yaml
on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
  workflow_dispatch:
```

- [ ] **Step 2: Rename job `verify` → `verify-no-soffice` and state its purpose**

```yaml
  # proves the library builds and tests green in an environment WITHOUT
  # LibreOffice: the soffice-gated ITs must skip cleanly, not fail
  verify-no-soffice:
    runs-on: ubuntu-latest
    timeout-minutes: 20
```

(keep the existing steps unchanged)

- [ ] **Step 3: Validate YAML + commit**

Run: `npx --yes js-yaml .github/workflows/build.yml` → exit 0.

```bash
git add .github/workflows/build.yml
git commit -m "$(cat <<'EOF'
ci: name the no-soffice job for what it proves, allow manual runs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: pin all actions to commit SHAs (both workflows)

**Files:**
- Modify: `.github/workflows/build.yml`, `.github/workflows/release.yml`

- [ ] **Step 1: Replace every `uses:` line**

In both files:
- `uses: actions/checkout@v4` → `uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1`
- `uses: actions/setup-java@v4` → `uses: actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9 # v4.8.0`

- [ ] **Step 2: Verify no unpinned uses remain**

Run: `grep -rn "uses:" .github/workflows/ | grep -v "# v"`
Expected: no output.

- [ ] **Step 3: Validate YAML + commit**

Run `npx --yes js-yaml` on both files → exit 0.

```bash
git add .github/workflows
git commit -m "$(cat <<'EOF'
ci: pin GitHub Actions to commit SHAs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: dependency-review job on pull requests

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Append the job at the end of `jobs:`**

```yaml
  # fails a PR that introduces dependencies with known CVEs (this project has
  # pinned CVE-patched transitives twice already — catch the next one early)
  dependency-review:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1
      - uses: actions/dependency-review-action@2031cfc080254a8a887f58cffee85186f0e49e48 # v4.9.0
```

- [ ] **Step 2: Validate YAML + commit**

Run: `npx --yes js-yaml .github/workflows/build.yml` → exit 0.

```bash
git add .github/workflows/build.yml
git commit -m "$(cat <<'EOF'
ci: fail PRs that introduce dependencies with known CVEs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Dependabot grouping

**Files:**
- Modify: `.github/dependabot.yml` (full new content below)

- [ ] **Step 1: Replace the file content**

```yaml
version: 2
updates:
  - package-ecosystem: maven
    directory: "/"
    schedule:
      interval: weekly
    groups:
      # majors stay as individual PRs — they deserve individual review
      maven-minor-patch:
        applies-to: version-updates
        update-types: ["minor", "patch"]
  - package-ecosystem: github-actions
    directory: "/"
    schedule:
      interval: weekly
    groups:
      actions:
        patterns: ["*"]
```

- [ ] **Step 2: Validate YAML + commit**

Run: `npx --yes js-yaml .github/dependabot.yml` → exit 0.

```bash
git add .github/dependabot.yml
git commit -m "$(cat <<'EOF'
ci: group Dependabot minor/patch updates into single weekly PRs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: changelog 0.2.0

**Files:**
- Create: `changelog/0.2.0.md`
- Modify: `changelog/README.md` (version summary at the top of the version list)

- [ ] **Step 1: Invoke the `writing-changelog` skill and follow it**

REQUIRED SUB-SKILL: `writing-changelog`. Scope: `git log v0.1.0..HEAD` (~45 commits). Key themes the entry must cover (verify each against the log before writing):
- **Изменено/breaking:** миграция на Java 25 + Spring Boot 3.5 (было: Boot 2.7); JXLS 3.1.0 (было 2.x, сняты CVE-пины 2.x-эры); выделение `doc-engine-api` (публичный API/SPI переехал в отдельный артефакт, builder — в пакет core).
- **Добавлено:** модуль `doc-engine-jodconverter` (пул LibreOffice-процессов, конфигурируемые порты/existing-process action); `GenerationOptions` builder; `TemplateReference.ofBytes/ofStream`; Spring config metadata с описаниями; `Automatic-Module-Name`; Windows-job и LibreOffice-гейт в CI; релиз по changelog-гейту с draft-релизом; maven-enforcer.
- **Исправлено:** cleanup конвертера на всех путях; жизненный цикл container-managed коллабораторов; дефолт `cleanup-on-shutdown`; null engine-hints в `RenderContext`.
- Дата: версия ещё не выпущена — следовать конвенции скилла/Keep a Changelog для невыпущенной версии (иначе дата проставится при теге).

- [ ] **Step 2: Commit**

```bash
git add changelog/
git commit -m "$(cat <<'EOF'
docs: changelog 0.2.0

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: integrate and watch CI

- [ ] **Step 1: Fast-forward merge to master, delete branch**

```bash
git checkout master && git merge --ff-only harness-batch-2 && git branch -d harness-batch-2
```

- [ ] **Step 2: Push and watch the build run**

```bash
git push origin master
```

Poll `https://api.github.com/repos/NikolayNN/doc-generator-engine/actions/runs?branch=master` until the run for the pushed SHA completes; report per-job results. Expected: `verify-no-soffice`, `windows`, `libreoffice-it` green; `dependency-review` absent (push event, not PR).
