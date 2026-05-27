# GitHub Publishing — Design

**Date:** 2026-05-27
**Status:** Approved (pending implementation plan)

## 1. Цель и границы

Настроить публикацию артефактов `doc-engine-core` и `doc-engine-spring-boot-starter` в **GitHub Packages** так, чтобы релиз запускался одним действием — git-тегом вида `v*`. Локальная разработка не должна замедляться (sources/javadoc собираются только на релизе).

### Не делаем

- Maven Central (требует верификации namespace, GPG-подписи, отдельного Sonatype-аккаунта).
- SNAPSHOT-канал из `master` (только тег-релизы).
- Maven Wrapper (на GitHub Runner Maven уже установлен).
- Ручные релизы через `maven-release-plugin` (версия пишется CI из тега).

## 2. Фиксированные решения

| Аспект | Решение |
|---|---|
| Реестр | GitHub Packages — `https://maven.pkg.github.com/NikolayNN/doc-generator-engine` |
| Owner/repo | `NikolayNN/doc-generator-engine` |
| groupId | `io.github.nikolaynn` (миграция с `com.example`) |
| Триггер релиза | git-тег `v*` → GitHub Actions |
| Версионирование | Источник истины — git-тег. В POM остаётся `0.1.0-SNAPSHOT`, CI делает `versions:set` |
| Sources/javadoc | Через профиль `release` (включён только при `mvn deploy` / `-Prelease`) |
| Лицензия | Apache License 2.0 |
| CI на PR | Отдельный workflow `build.yml` запускает `mvn -B verify` |

## 3. Архитектура изменений

### 3.1 Файлы и точки правок

```
doc-generator-engine/
├── pom.xml                              ← groupId, метаданные, distributionManagement, профиль release
├── doc-engine-core/pom.xml              ← <parent> groupId обновляется
├── doc-engine-spring-boot-starter/pom.xml ← <parent> groupId обновляется
├── LICENSE                              ← НОВЫЙ — текст Apache 2.0
├── README.md                            ← раздел «Установка из GitHub Packages»
└── .github/
    ├── maven-settings.xml               ← НОВЫЙ — server id=github c $GITHUB_ACTOR/$GITHUB_TOKEN
    └── workflows/
        ├── build.yml                    ← НОВЫЙ — CI на push/PR в master
        └── release.yml                  ← НОВЫЙ — публикация по тегу v*
```

### 3.2 Parent `pom.xml` — добавляемые блоки

```xml
<groupId>io.github.nikolaynn</groupId>
<artifactId>doc-generator-engine</artifactId>
<version>0.1.0-SNAPSHOT</version>

<name>Document Generator Engine</name>
<description>Java library for generating documents from XLSX templates (JXLS)
with optional PDF export via headless LibreOffice.</description>
<url>https://github.com/NikolayNN/doc-generator-engine</url>

<licenses>
  <license>
    <name>Apache License, Version 2.0</name>
    <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
  </license>
</licenses>

<developers>
  <developer>
    <id>NikolayNN</id>
    <name>Nikolay</name>
    <email>nikolay.horushko@gmail.com</email>
  </developer>
</developers>

<scm>
  <connection>scm:git:https://github.com/NikolayNN/doc-generator-engine.git</connection>
  <developerConnection>scm:git:git@github.com:NikolayNN/doc-generator-engine.git</developerConnection>
  <url>https://github.com/NikolayNN/doc-generator-engine</url>
  <tag>HEAD</tag>
</scm>

<distributionManagement>
  <repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/NikolayNN/doc-generator-engine</url>
  </repository>
  <snapshotRepository>
    <id>github</id>
    <url>https://maven.pkg.github.com/NikolayNN/doc-generator-engine</url>
  </snapshotRepository>
</distributionManagement>

<profiles>
  <profile>
    <id>release</id>
    <build>
      <plugins>
        <plugin>
          <artifactId>maven-source-plugin</artifactId>
          <executions>
            <execution>
              <id>attach-sources</id>
              <goals><goal>jar-no-fork</goal></goals>
            </execution>
          </executions>
        </plugin>
        <plugin>
          <artifactId>maven-javadoc-plugin</artifactId>
          <executions>
            <execution>
              <id>attach-javadocs</id>
              <goals><goal>jar</goal></goals>
            </execution>
          </executions>
          <configuration>
            <doclint>none</doclint>
            <failOnError>false</failOnError>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

`<plugin>` для source и javadoc также добавляются в `<pluginManagement>` parent pom с зафиксированными версиями (3.3.1 и 3.6.3 на момент написания).

### 3.3 `.github/maven-settings.xml`

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

`id=github` совпадает с `id` в `<distributionManagement>`.

### 3.4 `.github/workflows/release.yml`

```yaml
name: release
on:
  push:
    tags: ['v*']

permissions:
  contents: write       # для gh release create
  packages: write       # для publish в GitHub Packages

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Derive version from tag
        run: echo "RELEASE_VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_ENV
      - name: Set Maven version
        run: mvn -B versions:set -DnewVersion=$RELEASE_VERSION -DgenerateBackupPoms=false
      - name: Deploy
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: mvn -B -Prelease deploy --settings .github/maven-settings.xml
      - name: Create GitHub Release
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: gh release create "$GITHUB_REF_NAME" --generate-notes
```

`GITHUB_ACTOR` доступен как переменная окружения автоматически — отдельно прокидывать не нужно.

### 3.5 `.github/workflows/build.yml`

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
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: mvn -B verify
```

`mvn verify` гоняет юнит-тесты. `LibreOfficeConverterIT` и PDF-end-to-end-тесты пропускаются (`soffice` не установлен на runner-е, тесты под `@EnabledIf` это уже учитывают).

### 3.6 `LICENSE`

Стандартный текст Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt) с подставленным годом `2026` и владельцем `Nikolay`.

### 3.7 Обновление `README.md`

Добавляется раздел «Установка»:

```markdown
## Установка из GitHub Packages

GitHub Packages требует авторизации даже для публичных репозиториев.

`~/.m2/settings.xml`:

​```xml
<servers>
  <server>
    <id>github-nikolaynn</id>
    <username>ВАШ_GITHUB_LOGIN</username>
    <password>ghp_... (PAT с read:packages)</password>
  </server>
</servers>
​```

`pom.xml` потребителя:

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
```

Координаты `groupId`/`artifactId`/`version` в существующих примерах README обновляются на новый groupId.

## 4. Поток релиза

```
git tag v0.2.0
git push origin v0.2.0
       │
       ▼
GitHub Actions: release.yml
       │
   1. checkout @ v0.2.0
   2. setup-java 17 (temurin) + Maven cache
   3. RELEASE_VERSION=0.2.0
   4. mvn versions:set -DnewVersion=0.2.0
   5. mvn -Prelease deploy --settings .github/maven-settings.xml
       │
       ▼
GitHub Packages получает:
   • io.github.nikolaynn:doc-generator-engine:0.2.0          (parent pom)
   • io.github.nikolaynn:doc-engine-core:0.2.0               (jar + sources + javadoc)
   • io.github.nikolaynn:doc-engine-spring-boot-starter:0.2.0 (jar + sources + javadoc)
       │
   6. gh release create v0.2.0 --generate-notes
       ▼
GitHub Release со сгенерированным changelog'ом из коммитов и PR'ов
```

**Формат тега:** `v<semver>`. Поддерживаются квалификаторы: `v0.1.0-rc1`, `v0.1.0-beta`.

**Падение workflow.** GitHub Packages принимает только полные multi-file uploads, поэтому неуспешный deploy не оставляет «полу-артефакт» — перезапуск шага безопасен. Если упал шаг `gh release create` после успешного deploy — выполнить вручную, артефакт в Packages уже опубликован.

## 5. Безопасность и доступ

- В CI используется штатный `secrets.GITHUB_TOKEN`. Дополнительные PAT не нужны.
- `permissions: packages: write, contents: write` объявляются в workflow явно — не наследуем глобальные права репо.
- Для потребителей доступ — через PAT с правом `read:packages`. В README приведён пример, но сам токен в репо НЕ кладётся.
- `maven-settings.xml` в репозитории безопасен — он содержит **placeholder'ы** `${env.GITHUB_ACTOR}`/`${env.GITHUB_TOKEN}`, не секреты.

## 6. План смены пакетов в исходниках

При смене `groupId` пакеты Java остаются прежними (`com.example.docengine.*`) — Maven groupId и Java package — независимые координаты. В будущем при желании можно переименовать пакеты, но это отдельная задача и не блокирует первый релиз. Этот пункт фиксирует: **рефакторинг Java-пакетов в этот спек не входит**.

## 7. Тестирование изменений

- Локально: `mvn -Prelease package` — проверяет, что профиль `release` собирается, sources/javadoc JAR-ы создаются.
- Локально: `mvn -Prelease deploy -DaltDeploymentRepository=local::default::file:./target/local-repo` — dry-run публикации в локальную папку, без обращения к GitHub.
- CI: первый push тега `v0.1.0` — реальная проверка `release.yml`. Если упал — фиксим, тег **переиздаём** (`git tag -d v0.1.0 && git push --delete origin v0.1.0 && git tag v0.1.0 && git push origin v0.1.0`); это допустимо ровно до первого успешного релиза, после — каждый новый тег только инкрементальный.

## 8. Что закладывается под будущее

- **Maven Central.** `groupId=io.github.nikolaynn` уже совместим с правилами Central namespace без верификации домена. Понадобится: GPG-подпись (`maven-gpg-plugin`), второй distributionManagement repo, отдельный workflow `release-central.yml`. Существующий поток в GitHub Packages не сломается.
- **SNAPSHOT-канал** из `master` — добавляется отдельным workflow на `push: branches: [master]`. Версия становится `${date}-${shortSha}-SNAPSHOT`.
- **Подпись релизных commit'ов / тегов** — отдельная задача.
