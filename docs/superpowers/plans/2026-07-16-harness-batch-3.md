# Harness Batch 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining harness backlog: release tag-provenance gate, JaCoCo coverage floor, Maven Wrapper (pinned 3.9.16, used by CI), reproducible-build timestamp, javadoc lint in the release profile, and a project permission allowlist for Claude Code.

**Architecture:** Config/build-file edits plus one generated tool (mvnw). Executed on branch `harness-batch-3`, one commit per task, ff-merge to master, push, watch CI.

**Tech Stack:** GitHub Actions, Maven 3.9.16 wrapper, JaCoCo 0.8.15, maven-javadoc-plugin 3.12.0, Claude Code settings.

## Global Constraints

- Local `mvn` runs need `export JAVA_HOME='C:\Users\Nikolay\.jdks\jdk-25.0.3+9'` (until wrapper task lands, then `./mvnw` still needs JAVA_HOME for the JDK itself).
- Measured coverage today (instruction, per module): api 81.3%, core 88.9%, jodconverter 81.8%, starter 100% → floor **0.75** (below worst module with margin).
- Latest Maven 3.9.x fetched live: **3.9.16**.
- Action SHA already pinned in workflows: `actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0` — reuse verbatim where checkout options change.
- YAML check: `npx --yes js-yaml <file>` exits 0. Commits end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: release.yml — tag must be on master

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Give checkout full history** (ancestry check needs it; with `fetch-depth: 0` checkout fetches all branches and tags)

```yaml
      - uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
        with:
          fetch-depth: 0
```

- [ ] **Step 2: Add the gate right after "Derive version from tag"**

```yaml
      # a tag that isn't an ancestor of master never went through CI on master —
      # refuse to release it
      - name: Assert tag commit is on master
        run: |
          if ! git merge-base --is-ancestor "$GITHUB_SHA" origin/master; then
            echo "::error::tag $GITHUB_REF_NAME points at $GITHUB_SHA which is not on master"
            exit 1
          fi
```

- [ ] **Step 3: Validate YAML, sanity-check the git predicate locally**

Run: `npx --yes js-yaml .github/workflows/release.yml` → exit 0.
Run locally: `git merge-base --is-ancestor HEAD master && echo ON-MASTER; git merge-base --is-ancestor 1f197bc master && echo PR8-HEAD-REACHABLE || echo NOT-ANCESTOR`
Expected: `ON-MASTER`, then `NOT-ANCESTOR` is NOT printed for 1f197bc (it was merged, so it IS an ancestor — expect `PR8-HEAD-REACHABLE`). Use any unmerged SHA (e.g. `d5cade9`, PR #3 head) to see the negative: expect `NOT-ANCESTOR`.

- [ ] **Step 4: Commit** — `ci: refuse to release a tag that is not on master`

---

### Task 2: JaCoCo coverage floor

**Files:**
- Modify: `pom.xml` (properties + jacoco executions)

- [ ] **Step 1: Add property**

```xml
        <!-- instruction-coverage floor for jacoco:check; override per run:
             -Dcoverage.floor=0.99. Measured 2026-07-16: api 81%, core 89%,
             jodconverter 82%, starter 100% -->
        <coverage.floor>0.75</coverage.floor>
```

- [ ] **Step 2: Add check execution to the root jacoco plugin**

```xml
                    <execution>
                        <id>check-coverage</id>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>INSTRUCTION</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>${coverage.floor}</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
```

- [ ] **Step 3: Red** — run `mvn -B verify -Dcoverage.floor=0.99` → expect BUILD FAILURE with "instructions covered ratio is 0.81, but expected minimum is 0.99" on doc-engine-api.

- [ ] **Step 4: Green** — deferred to Task 5's single full `verify` (one build validates tasks 2+4+5 together).

- [ ] **Step 5: Commit** — `build: enforce 75% instruction-coverage floor via jacoco:check`

---

### Task 3: Maven Wrapper 3.9.16, CI uses it

**Files:**
- Modify: `.gitignore` (drop `.mvn/` line), `.github/workflows/build.yml`, `.github/workflows/release.yml`, `CLAUDE.md`
- Create (generated): `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`

- [ ] **Step 1: Remove `.mvn/` from `.gitignore`** (otherwise wrapper config can't be committed)

- [ ] **Step 2: Generate wrapper**

Run: `export JAVA_HOME='C:\Users\Nikolay\.jdks\jdk-25.0.3+9'; mvn -N wrapper:wrapper -Dmaven=3.9.16`
Expected: creates `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (distributionUrl ends `apache-maven-3.9.16-bin.zip`).

- [ ] **Step 3: Verify wrapper works**

Run: `./mvnw -v`
Expected: downloads 3.9.16, prints `Apache Maven 3.9.16`.

- [ ] **Step 4: Switch CI to the wrapper**

In `build.yml`: ubuntu jobs (`verify-no-soffice`, `libreoffice-it`) `run: mvn -B verify` → `run: ./mvnw -B verify`; windows job → `run: ./mvnw.cmd -B verify` (pwsh default shell resolves the cmd script). In `release.yml`: `mvn -B versions:set ...` → `./mvnw -B versions:set ...` and `mvn -B -Prelease deploy ...` → `./mvnw -B -Prelease deploy ...`.

- [ ] **Step 5: Update CLAUDE.md build section** — replace `mvn -B verify` with `./mvnw -B verify` and add one line: «Maven зафиксирован wrapper-ом (3.9.16); системный `mvn` больше не обязателен, JAVA_HOME — по-прежнему нужен».

- [ ] **Step 6: Validate YAML (both workflows), commit** — `build: pin Maven 3.9.16 via wrapper and use it in CI`

---

### Task 4: reproducible builds

**Files:**
- Modify: `pom.xml` (properties)

- [ ] **Step 1: Add property**

```xml
        <!-- reproducible builds; bump when cutting a release -->
        <project.build.outputTimestamp>2026-07-16T00:00:00Z</project.build.outputTimestamp>
```

- [ ] **Step 2: Commit** — `build: set outputTimestamp for reproducible artifacts`

---

### Task 5: javadoc lint in the release profile

**Files:**
- Modify: `pom.xml` (release profile javadoc config)

- [ ] **Step 1: Tighten config** — replace `<doclint>none</doclint>` / `<failOnError>false</failOnError>` with:

```xml
                        <configuration>
                            <!-- 'missing' излишне шумный; html/syntax/accessibility ловят
                                 реальные поломки разметки -->
                            <doclint>html,syntax,accessibility</doclint>
                            <failOnError>true</failOnError>
                            <quiet>true</quiet>
                        </configuration>
```

- [ ] **Step 2: Empirical check** — run `mvn -B -Prelease -DskipTests package`
Expected: BUILD SUCCESS (javadoc jars build clean). If javadoc errors appear: fix the offending Javadoc (they are real markup bugs), not the config; only if >10 errors, report to the human partner before proceeding.

- [ ] **Step 3: Green for tasks 2+4+5 together** — run `mvn -B verify`
Expected: BUILD SUCCESS incl. jacoco check-coverage at 0.75 floor.

- [ ] **Step 4: Commit** — `build: fail release javadoc on html/syntax/accessibility lint`

---

### Task 6: Claude Code permission allowlist

**Files:**
- Create/modify: `.claude/settings.json` (project, tracked), `.claude/settings.local.json` (cleanup)

- [ ] **Step 1: REQUIRED SUB-SKILL** — invoke `fewer-permission-prompts`, let it scan transcripts and write the project allowlist; review what it adds (read-only commands only).

- [ ] **Step 2: Clean `settings.local.json`** — drop the stale commit-pinned rule `Bash(git --no-pager diff --stat 90221ff~1 HEAD)`; keep generic entries.

- [ ] **Step 3: Commit `.claude/settings.json`** — `chore: project permission allowlist for Claude Code` (settings.local.json is git-ignored globally — not committed).

---

### Task 7: integrate and watch CI

- [ ] **Step 1:** `git checkout master && git merge --ff-only harness-batch-3 && git branch -d harness-batch-3 && git push origin master`
- [ ] **Step 2:** Poll the build run for the pushed SHA (authenticated with the stored PAT to avoid rate limits); expect `verify-no-soffice`, `windows`, `libreoffice-it` green — this also proves the wrapper works on all three runners. Report per-job results.
