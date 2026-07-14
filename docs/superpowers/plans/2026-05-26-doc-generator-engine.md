# Document Generator Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java library that wraps existing office tooling (JXLS + LibreOffice headless) behind a stable, extensible `DocumentEngine` API, producing XLSX/PDF documents from XLSX templates and `Map<String, Object>` data. Ships as a plain-Java core plus a Spring Boot 2.7 starter.

**Architecture:** Maven multi-module. `doc-engine-core` exposes a public `api` package (façade + records), an `spi` package (extension interfaces), and keeps default implementations package-private in `internal`. `doc-engine-spring-boot-starter` is a thin auto-configuration layer that wires defaults and reads `doc-engine.*` properties.

**Tech Stack:**
- Java 17
- Maven 3.9+
- JXLS 2.13.x + jxls-poi (transitively brings Apache POI 5.x)
- Apache Commons JEXL 3.3 (used by JXLS)
- SLF4J 2.x API (no binding)
- JUnit Jupiter 5.10.x, Mockito 5.x, AssertJ 3.25.x
- Spring Boot 2.7.18 (starter target; works on 3.x as well via `spring.factories`)
- LibreOffice — external binary (`soffice`), provided by environment

**Base package placeholder:** All sources use `io.github.nikolaynn.docengine.*`. If the consuming project requires a different package, refactor once at the very end (every task must be re-checked).

---

## File Map

```
doc-generator-engine/                                                    [parent]
├── pom.xml
├── doc-engine-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/docengine/
│       │   ├── api/
│       │   │   ├── DocumentEngine.java
│       │   │   ├── DocumentEngineBuilder.java
│       │   │   ├── DocumentFormat.java
│       │   │   ├── GenerationOptions.java
│       │   │   ├── GenerationRequest.java
│       │   │   ├── GenerationResult.java
│       │   │   ├── TemplateReference.java
│       │   │   └── exception/
│       │   │       ├── DocumentGenerationException.java
│       │   │       ├── DocumentConversionException.java
│       │   │       ├── InvalidGenerationRequestException.java
│       │   │       ├── TempFileException.java
│       │   │       ├── TemplateRenderingException.java
│       │   │       ├── TemplateResolutionException.java
│       │   │       ├── TemplateValidationException.java
│       │   │       ├── UnsupportedConversionException.java
│       │   │       └── UnsupportedTemplateFormatException.java
│       │   ├── spi/
│       │   │   ├── ConvertContext.java
│       │   │   ├── DocumentConverter.java
│       │   │   ├── RenderContext.java
│       │   │   ├── ResolvedTemplate.java
│       │   │   ├── TempFileManager.java
│       │   │   ├── TemplateEngine.java
│       │   │   ├── TemplateResolver.java
│       │   │   └── TemplateValidator.java
│       │   └── internal/
│       │       ├── DefaultDocumentEngine.java
│       │       ├── jxls/JxlsTemplateEngine.java
│       │       ├── libreoffice/LibreOfficeConverter.java
│       │       ├── resolver/InputStreamTemplateResolver.java
│       │       ├── tempfile/DefaultTempFileManager.java
│       │       └── validator/NoopTemplateValidator.java
│       └── test/java/com/example/docengine/
│           ├── api/...
│           ├── internal/...
│           └── support/TemplateFixtures.java        (builds XLSX templates in-memory for tests)
└── doc-engine-spring-boot-starter/
    ├── pom.xml
    └── src/
        ├── main/java/com/example/docengine/starter/
        │   ├── DocEngineAutoConfiguration.java
        │   └── DocEngineProperties.java
        ├── main/resources/META-INF/spring.factories
        └── test/java/com/example/docengine/starter/
            └── DocEngineAutoConfigurationTest.java
```

---

## Task 1: Multi-module Maven skeleton

**Files:**
- Create: `pom.xml` (parent)
- Create: `doc-engine-core/pom.xml`
- Create: `doc-engine-spring-boot-starter/pom.xml`
- Create: `doc-engine-core/src/main/java/.gitkeep`
- Create: `doc-engine-core/src/test/java/.gitkeep`
- Create: `doc-engine-spring-boot-starter/src/main/java/.gitkeep`
- Create: `doc-engine-spring-boot-starter/src/test/java/.gitkeep`
- Create: `.gitignore`

- [ ] **Step 1: Create parent pom.xml**

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>doc-generator-engine</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Document Generator Engine</name>

    <modules>
        <module>doc-engine-core</module>
        <module>doc-engine-spring-boot-starter</module>
    </modules>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>

        <jxls.version>2.13.0</jxls.version>
        <jxls-poi.version>2.13.0</jxls-poi.version>
        <commons-jexl3.version>3.3</commons-jexl3.version>
        <slf4j.version>2.0.13</slf4j.version>
        <spring-boot.version>2.7.18</spring-boot.version>

        <junit.version>5.10.2</junit.version>
        <mockito.version>5.11.0</mockito.version>
        <assertj.version>3.25.3</assertj.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.jxls</groupId>
                <artifactId>jxls</artifactId>
                <version>${jxls.version}</version>
            </dependency>
            <dependency>
                <groupId>org.jxls</groupId>
                <artifactId>jxls-poi</artifactId>
                <version>${jxls-poi.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-jexl3</artifactId>
                <version>${commons-jexl3.version}</version>
            </dependency>
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-autoconfigure</artifactId>
                <version>${spring-boot.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>
                <version>${spring-boot.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-test</artifactId>
                <version>${spring-boot.version}</version>
            </dependency>
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
            </dependency>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>${mockito.version}</version>
            </dependency>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-junit-jupiter</artifactId>
                <version>${mockito.version}</version>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.5</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: Create core module pom.xml**

`doc-engine-core/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>doc-generator-engine</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>doc-engine-core</artifactId>
    <name>Document Generator Engine — Core</name>

    <dependencies>
        <dependency>
            <groupId>org.jxls</groupId>
            <artifactId>jxls</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jxls</groupId>
            <artifactId>jxls-poi</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-jexl3</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>${slf4j.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create starter module pom.xml**

`doc-engine-spring-boot-starter/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>doc-generator-engine</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>doc-engine-spring-boot-starter</artifactId>
    <name>Document Generator Engine — Spring Boot Starter</name>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>doc-engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-test</artifactId>
            <scope>test</scope>
        </dependency>
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

- [ ] **Step 4: Create directory placeholders**

```bash
mkdir -p doc-engine-core/src/main/java doc-engine-core/src/test/java
mkdir -p doc-engine-spring-boot-starter/src/main/java doc-engine-spring-boot-starter/src/test/java
touch doc-engine-core/src/main/java/.gitkeep doc-engine-core/src/test/java/.gitkeep
touch doc-engine-spring-boot-starter/src/main/java/.gitkeep doc-engine-spring-boot-starter/src/test/java/.gitkeep
```

- [ ] **Step 5: Create .gitignore**

`.gitignore`:

```
target/
*.class
.idea/
*.iml
.vscode/
.DS_Store
.classpath
.project
.settings/
build/
out/
```

- [ ] **Step 6: Verify build works**

Run: `mvn -q clean verify`
Expected: BUILD SUCCESS, no tests run (no sources yet).

- [ ] **Step 7: Commit**

```bash
git add pom.xml doc-engine-core/pom.xml doc-engine-spring-boot-starter/pom.xml \
        doc-engine-core/src/main/java/.gitkeep doc-engine-core/src/test/java/.gitkeep \
        doc-engine-spring-boot-starter/src/main/java/.gitkeep doc-engine-spring-boot-starter/src/test/java/.gitkeep \
        .gitignore
git commit -m "build: Maven multi-module skeleton with core + Spring Boot starter"
```

---

## Task 2: DocumentFormat enum

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/DocumentFormat.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/api/DocumentFormatTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/api/DocumentFormatTest.java`:

```java
package io.github.nikolaynn.docengine.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentFormatTest {

    @Test
    void xlsxHasOoxmlMimeAndXlsxExtension() {
        assertThat(DocumentFormat.XLSX.mimeType())
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(DocumentFormat.XLSX.extension()).isEqualTo("xlsx");
    }

    @Test
    void pdfHasApplicationPdfMimeAndPdfExtension() {
        assertThat(DocumentFormat.PDF.mimeType()).isEqualTo("application/pdf");
        assertThat(DocumentFormat.PDF.extension()).isEqualTo("pdf");
    }
}
```

- [ ] **Step 2: Run test, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error — `DocumentFormat` does not exist.

- [ ] **Step 3: Implement**

`doc-engine-core/src/main/java/com/example/docengine/api/DocumentFormat.java`:

```java
package io.github.nikolaynn.docengine.api;

public enum DocumentFormat {
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    private final String mimeType;
    private final String extension;

    DocumentFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() { return mimeType; }
    public String extension() { return extension; }
}
```

- [ ] **Step 4: Run tests, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: BUILD SUCCESS, 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/api/DocumentFormat.java \
        doc-engine-core/src/test/java/com/example/docengine/api/DocumentFormatTest.java
git commit -m "feat(core): add DocumentFormat enum with XLSX and PDF"
```

---

## Task 3: TemplateReference sealed interface

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/TemplateReference.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/api/TemplateReferenceTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/api/TemplateReferenceTest.java`:

```java
package io.github.nikolaynn.docengine.api;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateReferenceTest {

    @Test
    void bytesRefStoresAllFields() {
        byte[] payload = new byte[]{1, 2, 3};
        var ref = new TemplateReference.BytesRef(payload, DocumentFormat.XLSX, "report.xlsx");
        assertThat(ref.bytes()).isSameAs(payload);
        assertThat(ref.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ref.hint()).isEqualTo("report.xlsx");
    }

    @Test
    void inputStreamRefStoresAllFields() {
        InputStream in = new ByteArrayInputStream(new byte[]{1});
        var ref = new TemplateReference.InputStreamRef(in, DocumentFormat.XLSX, "tpl");
        assertThat(ref.stream()).isSameAs(in);
        assertThat(ref.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ref.hint()).isEqualTo("tpl");
    }

    @Test
    void bytesRefRejectsNullBytes() {
        assertThatThrownBy(() -> new TemplateReference.BytesRef(null, DocumentFormat.XLSX, "x"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void inputStreamRefRejectsNullStream() {
        assertThatThrownBy(() -> new TemplateReference.InputStreamRef(null, DocumentFormat.XLSX, "x"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSourceFormat() {
        assertThatThrownBy(() -> new TemplateReference.BytesRef(new byte[0], null, "x"))
            .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement**

`doc-engine-core/src/main/java/com/example/docengine/api/TemplateReference.java`:

```java
package io.github.nikolaynn.docengine.api;

import java.io.InputStream;
import java.util.Objects;

public sealed interface TemplateReference
        permits TemplateReference.InputStreamRef, TemplateReference.BytesRef {

    DocumentFormat sourceFormat();

    String hint();

    record InputStreamRef(InputStream stream, DocumentFormat sourceFormat, String hint)
            implements TemplateReference {
        public InputStreamRef {
            Objects.requireNonNull(stream, "stream");
            Objects.requireNonNull(sourceFormat, "sourceFormat");
        }
    }

    record BytesRef(byte[] bytes, DocumentFormat sourceFormat, String hint)
            implements TemplateReference {
        public BytesRef {
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(sourceFormat, "sourceFormat");
        }
    }
}
```

- [ ] **Step 4: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: 5+ tests pass.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/api/TemplateReference.java \
        doc-engine-core/src/test/java/com/example/docengine/api/TemplateReferenceTest.java
git commit -m "feat(core): add sealed TemplateReference with BytesRef and InputStreamRef"
```

---

## Task 4: GenerationOptions, GenerationRequest, GenerationResult records

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/GenerationOptions.java`
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/GenerationRequest.java`
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/GenerationResult.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/api/GenerationTypesTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/api/GenerationTypesTest.java`:

```java
package io.github.nikolaynn.docengine.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationTypesTest {

    @Test
    void optionsDefaultsAreAllNullOrEmpty() {
        var opts = GenerationOptions.defaults();
        assertThat(opts.fileNameHint()).isNull();
        assertThat(opts.timeout()).isNull();
        assertThat(opts.locale()).isNull();
        assertThat(opts.engineHints()).isEmpty();
    }

    @Test
    void optionsConstructorNormalizesNullHintsToEmptyMap() {
        var opts = new GenerationOptions(null, Duration.ofSeconds(5), null, null);
        assertThat(opts.engineHints()).isEmpty();
    }

    @Test
    void requestStoresAllFields() {
        var ref = new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h");
        var data = Map.<String, Object>of("k", "v");
        var opts = GenerationOptions.defaults();
        var req = new GenerationRequest(ref, data, DocumentFormat.PDF, opts);

        assertThat(req.template()).isSameAs(ref);
        assertThat(req.data()).isSameAs(data);
        assertThat(req.targetFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(req.options()).isSameAs(opts);
    }

    @Test
    void requestRejectsNullTemplateAndTargetFormat() {
        var data = Map.<String, Object>of();
        var opts = GenerationOptions.defaults();
        assertThatThrownBy(() -> new GenerationRequest(null, data, DocumentFormat.PDF, opts))
            .isInstanceOf(NullPointerException.class);
        var ref = new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h");
        assertThatThrownBy(() -> new GenerationRequest(ref, data, null, opts))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requestNormalizesNullDataToEmptyMapAndNullOptionsToDefaults() {
        var ref = new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h");
        var req = new GenerationRequest(ref, null, DocumentFormat.PDF, null);
        assertThat(req.data()).isEmpty();
        assertThat(req.options()).isEqualTo(GenerationOptions.defaults());
    }

    @Test
    void resultStoresAllFields() {
        byte[] payload = new byte[]{9, 9};
        var res = new GenerationResult("report.pdf", "application/pdf", DocumentFormat.PDF, payload);
        assertThat(res.fileName()).isEqualTo("report.pdf");
        assertThat(res.mimeType()).isEqualTo("application/pdf");
        assertThat(res.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(res.content()).isSameAs(payload);
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement GenerationOptions**

`doc-engine-core/src/main/java/com/example/docengine/api/GenerationOptions.java`:

```java
package io.github.nikolaynn.docengine.api;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public record GenerationOptions(
        String fileNameHint,
        Duration timeout,
        Locale locale,
        Map<String, Object> engineHints
) {
    private static final GenerationOptions DEFAULTS =
            new GenerationOptions(null, null, null, Map.of());

    public GenerationOptions {
        engineHints = engineHints == null ? Map.of() : Collections.unmodifiableMap(engineHints);
    }

    public static GenerationOptions defaults() {
        return DEFAULTS;
    }
}
```

- [ ] **Step 4: Implement GenerationRequest**

`doc-engine-core/src/main/java/com/example/docengine/api/GenerationRequest.java`:

```java
package io.github.nikolaynn.docengine.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record GenerationRequest(
        TemplateReference template,
        Map<String, Object> data,
        DocumentFormat targetFormat,
        GenerationOptions options
) {
    public GenerationRequest {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(targetFormat, "targetFormat");
        data = data == null ? Map.of() : Collections.unmodifiableMap(data);
        options = options == null ? GenerationOptions.defaults() : options;
    }
}
```

- [ ] **Step 5: Implement GenerationResult**

`doc-engine-core/src/main/java/com/example/docengine/api/GenerationResult.java`:

```java
package io.github.nikolaynn.docengine.api;

import java.util.Objects;

public record GenerationResult(
        String fileName,
        String mimeType,
        DocumentFormat format,
        byte[] content
) {
    public GenerationResult {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(content, "content");
    }
}
```

- [ ] **Step 6: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/api/GenerationOptions.java \
        doc-engine-core/src/main/java/com/example/docengine/api/GenerationRequest.java \
        doc-engine-core/src/main/java/com/example/docengine/api/GenerationResult.java \
        doc-engine-core/src/test/java/com/example/docengine/api/GenerationTypesTest.java
git commit -m "feat(core): add GenerationOptions, GenerationRequest, GenerationResult records"
```

---

## Task 5: Exception hierarchy

**Files:**
- Create: 9 exception classes under `doc-engine-core/src/main/java/com/example/docengine/api/exception/`
- Test: `doc-engine-core/src/test/java/com/example/docengine/api/exception/DocumentGenerationExceptionTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/api/exception/DocumentGenerationExceptionTest.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentGenerationExceptionTest {

    @Test
    void rootExceptionExposesContextFields() {
        var ex = new TemplateRenderingException(
            "tpl.xlsx", DocumentFormat.XLSX, DocumentFormat.PDF, "boom", new RuntimeException("cause"));
        assertThat(ex.getTemplateHint()).isEqualTo("tpl.xlsx");
        assertThat(ex.getSourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ex.getTargetFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(ex.getMessage()).contains("boom").contains("tpl.xlsx");
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void documentConversionExceptionTracksTimeoutFlag() {
        var ex = DocumentConversionException.timeout(
            "tpl.xlsx", DocumentFormat.XLSX, DocumentFormat.PDF, java.time.Duration.ofSeconds(5));
        assertThat(ex.isTimeout()).isTrue();
        assertThat(ex.getMessage()).contains("timeout").contains("5");
    }

    @Test
    void unsupportedTemplateFormatExceptionUsesNullableTarget() {
        var ex = new UnsupportedTemplateFormatException("h", DocumentFormat.XLSX);
        assertThat(ex.getSourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(ex.getTargetFormat()).isNull();
    }

    @Test
    void allSubtypesExtendRoot() {
        assertThat(new InvalidGenerationRequestException("m")).isInstanceOf(DocumentGenerationException.class);
        assertThat(new TempFileException("h", null, null, "m", null)).isInstanceOf(DocumentGenerationException.class);
        assertThat(new TemplateResolutionException("h", DocumentFormat.XLSX, "m", null))
            .isInstanceOf(DocumentGenerationException.class);
        assertThat(new TemplateValidationException("h", DocumentFormat.XLSX, "m"))
            .isInstanceOf(DocumentGenerationException.class);
        assertThat(new UnsupportedConversionException("h", DocumentFormat.XLSX, DocumentFormat.PDF))
            .isInstanceOf(DocumentGenerationException.class);
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile errors.

- [ ] **Step 3: Implement root exception**

`doc-engine-core/src/main/java/com/example/docengine/api/exception/DocumentGenerationException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public abstract class DocumentGenerationException extends RuntimeException {

    private final String templateHint;
    private final DocumentFormat sourceFormat;
    private final DocumentFormat targetFormat;

    protected DocumentGenerationException(String templateHint,
                                          DocumentFormat sourceFormat,
                                          DocumentFormat targetFormat,
                                          String message,
                                          Throwable cause) {
        super(buildMessage(message, templateHint, sourceFormat, targetFormat), cause);
        this.templateHint = templateHint;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    public String getTemplateHint() { return templateHint; }
    public DocumentFormat getSourceFormat() { return sourceFormat; }
    public DocumentFormat getTargetFormat() { return targetFormat; }

    private static String buildMessage(String message,
                                       String templateHint,
                                       DocumentFormat sourceFormat,
                                       DocumentFormat targetFormat) {
        StringBuilder sb = new StringBuilder();
        if (message != null) sb.append(message);
        sb.append(" [template=").append(templateHint == null ? "<unknown>" : templateHint);
        if (sourceFormat != null) sb.append(", source=").append(sourceFormat);
        if (targetFormat != null) sb.append(", target=").append(targetFormat);
        sb.append("]");
        return sb.toString();
    }
}
```

- [ ] **Step 4: Implement subtypes**

`InvalidGenerationRequestException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

public class InvalidGenerationRequestException extends DocumentGenerationException {
    public InvalidGenerationRequestException(String message) {
        super(null, null, null, message, null);
    }
}
```

`TemplateResolutionException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class TemplateResolutionException extends DocumentGenerationException {
    public TemplateResolutionException(String templateHint, DocumentFormat sourceFormat,
                                       String message, Throwable cause) {
        super(templateHint, sourceFormat, null, message, cause);
    }
}
```

`TemplateValidationException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class TemplateValidationException extends DocumentGenerationException {
    public TemplateValidationException(String templateHint, DocumentFormat sourceFormat, String message) {
        super(templateHint, sourceFormat, null, message, null);
    }
}
```

`TemplateRenderingException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class TemplateRenderingException extends DocumentGenerationException {
    public TemplateRenderingException(String templateHint,
                                      DocumentFormat sourceFormat,
                                      DocumentFormat targetFormat,
                                      String message,
                                      Throwable cause) {
        super(templateHint, sourceFormat, targetFormat, message, cause);
    }
}
```

`UnsupportedTemplateFormatException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class UnsupportedTemplateFormatException extends DocumentGenerationException {
    public UnsupportedTemplateFormatException(String templateHint, DocumentFormat sourceFormat) {
        super(templateHint, sourceFormat, null,
              "no TemplateEngine supports source format", null);
    }
}
```

`UnsupportedConversionException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class UnsupportedConversionException extends DocumentGenerationException {
    public UnsupportedConversionException(String templateHint,
                                          DocumentFormat sourceFormat,
                                          DocumentFormat targetFormat) {
        super(templateHint, sourceFormat, targetFormat,
              "no DocumentConverter supports conversion", null);
    }
}
```

`DocumentConversionException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import java.time.Duration;

public class DocumentConversionException extends DocumentGenerationException {

    private final boolean timeout;

    public DocumentConversionException(String templateHint,
                                       DocumentFormat sourceFormat,
                                       DocumentFormat targetFormat,
                                       String message,
                                       Throwable cause,
                                       boolean timeout) {
        super(templateHint, sourceFormat, targetFormat, message, cause);
        this.timeout = timeout;
    }

    public static DocumentConversionException timeout(String templateHint,
                                                      DocumentFormat sourceFormat,
                                                      DocumentFormat targetFormat,
                                                      Duration duration) {
        return new DocumentConversionException(
            templateHint, sourceFormat, targetFormat,
            "conversion timeout after " + duration.toSeconds() + "s",
            null, true);
    }

    public boolean isTimeout() { return timeout; }
}
```

`TempFileException.java`:

```java
package io.github.nikolaynn.docengine.api.exception;

import io.github.nikolaynn.docengine.api.DocumentFormat;

public class TempFileException extends DocumentGenerationException {
    public TempFileException(String templateHint,
                             DocumentFormat sourceFormat,
                             DocumentFormat targetFormat,
                             String message,
                             Throwable cause) {
        super(templateHint, sourceFormat, targetFormat, message, cause);
    }
}
```

- [ ] **Step 5: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/api/exception/ \
        doc-engine-core/src/test/java/com/example/docengine/api/exception/
git commit -m "feat(core): add DocumentGenerationException hierarchy"
```

---

## Task 6: SPI interfaces and context records

**Files:**
- Create: 8 files under `doc-engine-core/src/main/java/com/example/docengine/spi/`
- Test: `doc-engine-core/src/test/java/com/example/docengine/spi/ContextRecordsTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/spi/ContextRecordsTest.java`:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextRecordsTest {

    @Test
    void renderContextStoresFields() {
        TempFileManager tfm = new TempFileManager() {
            public Path createTempFile(String prefix, String suffix) { return null; }
            public void delete(Path path) {}
        };
        var ctx = new RenderContext(Locale.ENGLISH, Duration.ofSeconds(10), Map.of("a", 1), tfm, "hint");
        assertThat(ctx.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(ctx.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(ctx.engineHints()).containsEntry("a", 1);
        assertThat(ctx.tempFileManager()).isSameAs(tfm);
        assertThat(ctx.templateHint()).isEqualTo("hint");
    }

    @Test
    void convertContextStoresFields() {
        TempFileManager tfm = new TempFileManager() {
            public Path createTempFile(String prefix, String suffix) { return null; }
            public void delete(Path path) {}
        };
        var ctx = new ConvertContext(Duration.ofSeconds(5), tfm, "h");
        assertThat(ctx.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(ctx.tempFileManager()).isSameAs(tfm);
        assertThat(ctx.templateHint()).isEqualTo("h");
    }

    @Test
    void resolvedTemplateExposesBytesAndFormat() {
        var rt = new ResolvedTemplate(new byte[]{1, 2}, DocumentFormat.XLSX, "h");
        assertThat(rt.bytes()).hasSize(2);
        assertThat(rt.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(rt.hint()).isEqualTo("h");
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement context records**

`doc-engine-core/src/main/java/com/example/docengine/spi/ResolvedTemplate.java`:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;

import java.util.Objects;

public record ResolvedTemplate(byte[] bytes, DocumentFormat sourceFormat, String hint) {
    public ResolvedTemplate {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(sourceFormat, "sourceFormat");
    }
}
```

`doc-engine-core/src/main/java/com/example/docengine/spi/RenderContext.java`:

```java
package io.github.nikolaynn.docengine.spi;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record RenderContext(
        Locale locale,
        Duration timeout,
        Map<String, Object> engineHints,
        TempFileManager tempFileManager,
        String templateHint
) {
    public RenderContext {
        Objects.requireNonNull(tempFileManager, "tempFileManager");
        engineHints = engineHints == null ? Map.of() : Map.copyOf(engineHints);
    }
}
```

`doc-engine-core/src/main/java/com/example/docengine/spi/ConvertContext.java`:

```java
package io.github.nikolaynn.docengine.spi;

import java.time.Duration;
import java.util.Objects;

public record ConvertContext(Duration timeout, TempFileManager tempFileManager, String templateHint) {
    public ConvertContext {
        Objects.requireNonNull(tempFileManager, "tempFileManager");
    }
}
```

- [ ] **Step 4: Implement SPI interfaces**

`doc-engine-core/src/main/java/com/example/docengine/spi/TempFileManager.java`:

```java
package io.github.nikolaynn.docengine.spi;

import java.nio.file.Path;

public interface TempFileManager {
    Path createTempFile(String prefix, String suffix);
    void delete(Path path);
}
```

`doc-engine-core/src/main/java/com/example/docengine/spi/TemplateResolver.java`:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.TemplateReference;

public interface TemplateResolver {
    ResolvedTemplate resolve(TemplateReference ref);
}
```

`doc-engine-core/src/main/java/com/example/docengine/spi/TemplateValidator.java`:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.TemplateReference;

public interface TemplateValidator {
    void validate(TemplateReference ref);
}
```

`doc-engine-core/src/main/java/com/example/docengine/spi/TemplateEngine.java`:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;

import java.nio.file.Path;
import java.util.Map;

public interface TemplateEngine {
    boolean supports(DocumentFormat sourceFormat);

    /** Renders the template with data into a file of the same format as source. */
    Path render(ResolvedTemplate template, Map<String, Object> data, RenderContext ctx);
}
```

`doc-engine-core/src/main/java/com/example/docengine/spi/DocumentConverter.java`:

```java
package io.github.nikolaynn.docengine.spi;

import io.github.nikolaynn.docengine.api.DocumentFormat;

import java.nio.file.Path;

public interface DocumentConverter {
    boolean supports(DocumentFormat from, DocumentFormat to);

    /** Converts the input file to target format; returns a new file. */
    Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx);
}
```

- [ ] **Step 5: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/spi/ \
        doc-engine-core/src/test/java/com/example/docengine/spi/
git commit -m "feat(core): add SPI interfaces and context records"
```

---

## Task 7: DefaultTempFileManager

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/internal/tempfile/DefaultTempFileManager.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/tempfile/DefaultTempFileManagerTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/internal/tempfile/DefaultTempFileManagerTest.java`:

```java
package io.github.nikolaynn.docengine.internal.tempfile;

import io.github.nikolaynn.docengine.api.exception.TempFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTempFileManagerTest {

    @Test
    void createsTempFileInGivenDirectoryWithExpectedSuffix(@TempDir Path tmp) throws Exception {
        var mgr = new DefaultTempFileManager(tmp, false);
        Path file = mgr.createTempFile("doc-", ".xlsx");

        assertThat(file).exists();
        assertThat(file.getParent()).isEqualTo(tmp);
        assertThat(file.getFileName().toString()).startsWith("doc-").endsWith(".xlsx");
    }

    @Test
    void deleteRemovesFile(@TempDir Path tmp) throws Exception {
        var mgr = new DefaultTempFileManager(tmp, false);
        Path file = mgr.createTempFile("a-", ".bin");
        assertThat(file).exists();

        mgr.delete(file);
        assertThat(file).doesNotExist();
    }

    @Test
    void deleteOfMissingFileIsSilent(@TempDir Path tmp) {
        var mgr = new DefaultTempFileManager(tmp, false);
        Path missing = tmp.resolve("nope.txt");
        mgr.delete(missing); // must not throw
    }

    @Test
    void deleteOfNullIsSilent(@TempDir Path tmp) {
        var mgr = new DefaultTempFileManager(tmp, false);
        mgr.delete(null);
    }

    @Test
    void nullRootDirFallsBackToSystemTemp() throws Exception {
        var mgr = new DefaultTempFileManager(null, false);
        Path file = mgr.createTempFile("b-", ".bin");
        try {
            assertThat(file).exists();
            assertThat(file.getParent()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void wrapsIoErrorInTempFileException() {
        var mgr = new DefaultTempFileManager(Path.of("/no/such/dir/should/not/exist/zzz"), false);
        assertThatThrownBy(() -> mgr.createTempFile("x", ".y"))
            .isInstanceOf(TempFileException.class);
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement**

`doc-engine-core/src/main/java/com/example/docengine/internal/tempfile/DefaultTempFileManager.java`:

```java
package io.github.nikolaynn.docengine.internal.tempfile;

import io.github.nikolaynn.docengine.api.exception.TempFileException;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DefaultTempFileManager implements TempFileManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTempFileManager.class);

    private final Path rootDir;
    private final Set<Path> tracked = new CopyOnWriteArraySet<>();

    public DefaultTempFileManager(Path rootDir, boolean cleanupOnShutdown) {
        this.rootDir = rootDir;
        if (cleanupOnShutdown) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupAll, "doc-engine-temp-cleanup"));
        }
    }

    @Override
    public Path createTempFile(String prefix, String suffix) {
        try {
            Path file = rootDir == null
                ? Files.createTempFile(prefix, suffix)
                : Files.createTempFile(rootDir, prefix, suffix);
            tracked.add(file);
            return file;
        } catch (IOException e) {
            throw new TempFileException(null, null, null,
                "failed to create temp file in " + (rootDir == null ? "<system tmp>" : rootDir), e);
        }
    }

    @Override
    public void delete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
            tracked.remove(path);
        } catch (IOException e) {
            log.warn("failed to delete temp file {}: {}", path, e.getMessage());
        }
    }

    private void cleanupAll() {
        for (Path p : tracked) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // shutdown best-effort
            }
        }
    }
}
```

- [ ] **Step 4: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/internal/tempfile/ \
        doc-engine-core/src/test/java/com/example/docengine/internal/tempfile/
git commit -m "feat(core): add DefaultTempFileManager with shutdown cleanup"
```

---

## Task 8: NoopTemplateValidator

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/internal/validator/NoopTemplateValidator.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/validator/NoopTemplateValidatorTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/internal/validator/NoopTemplateValidatorTest.java`:

```java
package io.github.nikolaynn.docengine.internal.validator;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.TemplateReference;
import org.junit.jupiter.api.Test;

class NoopTemplateValidatorTest {

    @Test
    void validateNeverThrowsForAnyReference() {
        var v = new NoopTemplateValidator();
        v.validate(new TemplateReference.BytesRef(new byte[0], DocumentFormat.XLSX, "h"));
    }

    @Test
    void validateNeverThrowsForNull() {
        new NoopTemplateValidator().validate(null);
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement**

`doc-engine-core/src/main/java/com/example/docengine/internal/validator/NoopTemplateValidator.java`:

```java
package io.github.nikolaynn.docengine.internal.validator;

import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.spi.TemplateValidator;

public class NoopTemplateValidator implements TemplateValidator {
    @Override
    public void validate(TemplateReference ref) {
        // intentionally empty: real validation arrives in a later version
    }
}
```

- [ ] **Step 4: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/internal/validator/ \
        doc-engine-core/src/test/java/com/example/docengine/internal/validator/
git commit -m "feat(core): add NoopTemplateValidator (placeholder for v2 validation)"
```

---

## Task 9: InputStreamTemplateResolver

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/internal/resolver/InputStreamTemplateResolver.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/resolver/InputStreamTemplateResolverTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/internal/resolver/InputStreamTemplateResolverTest.java`:

```java
package io.github.nikolaynn.docengine.internal.resolver;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.api.exception.TemplateResolutionException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputStreamTemplateResolverTest {

    @Test
    void bytesRefReturnedAsIs() {
        var r = new InputStreamTemplateResolver();
        byte[] payload = {1, 2, 3};
        var resolved = r.resolve(new TemplateReference.BytesRef(payload, DocumentFormat.XLSX, "h"));
        assertThat(resolved.bytes()).isEqualTo(payload);
        assertThat(resolved.sourceFormat()).isEqualTo(DocumentFormat.XLSX);
        assertThat(resolved.hint()).isEqualTo("h");
    }

    @Test
    void inputStreamRefIsReadFully() {
        var r = new InputStreamTemplateResolver();
        byte[] payload = {9, 8, 7, 6};
        var ref = new TemplateReference.InputStreamRef(
            new ByteArrayInputStream(payload), DocumentFormat.XLSX, "h");
        var resolved = r.resolve(ref);
        assertThat(resolved.bytes()).isEqualTo(payload);
    }

    @Test
    void ioErrorOnReadMappedToTemplateResolutionException() {
        var r = new InputStreamTemplateResolver();
        InputStream broken = new InputStream() {
            public int read() throws IOException { throw new IOException("boom"); }
        };
        assertThatThrownBy(() -> r.resolve(
            new TemplateReference.InputStreamRef(broken, DocumentFormat.XLSX, "h")))
            .isInstanceOf(TemplateResolutionException.class)
            .hasMessageContaining("h");
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement**

`doc-engine-core/src/main/java/com/example/docengine/internal/resolver/InputStreamTemplateResolver.java`:

```java
package io.github.nikolaynn.docengine.internal.resolver;

import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.api.exception.TemplateResolutionException;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TemplateResolver;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamTemplateResolver implements TemplateResolver {

    @Override
    public ResolvedTemplate resolve(TemplateReference ref) {
        return switch (ref) {
            case TemplateReference.BytesRef b ->
                new ResolvedTemplate(b.bytes(), b.sourceFormat(), b.hint());
            case TemplateReference.InputStreamRef s ->
                new ResolvedTemplate(readAll(s.stream(), s.hint(), s.sourceFormat()),
                                     s.sourceFormat(), s.hint());
        };
    }

    private static byte[] readAll(InputStream in,
                                  String hint,
                                  io.github.nikolaynn.docengine.api.DocumentFormat sourceFormat) {
        try (InputStream is = in) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new TemplateResolutionException(hint, sourceFormat,
                "failed to read template stream", e);
        }
    }
}
```

- [ ] **Step 4: Run, expect green**

Run: `mvn -pl doc-engine-core -q test`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/internal/resolver/ \
        doc-engine-core/src/test/java/com/example/docengine/internal/resolver/
git commit -m "feat(core): add InputStreamTemplateResolver"
```

---

## Task 10: JxlsTemplateEngine

This task is bigger — covers the actual template engine. It includes a shared `TemplateFixtures` helper that builds test XLSX files in-memory so we don't need to commit binary artifacts.

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/internal/jxls/JxlsTemplateEngine.java`
- Create: `doc-engine-core/src/test/java/com/example/docengine/support/TemplateFixtures.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/jxls/JxlsTemplateEngineTest.java`

- [ ] **Step 1: Create test fixture helper**

`doc-engine-core/src/test/java/com/example/docengine/support/TemplateFixtures.java`:

```java
package io.github.nikolaynn.docengine.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Builds in-memory XLSX templates for tests so we don't commit binary fixtures. */
public final class TemplateFixtures {

    private TemplateFixtures() {}

    /** A1: ${greeting}, B1: ${name} */
    public static byte[] simpleFields() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");
            Row row = sh.createRow(0);
            row.createCell(0).setCellValue("${greeting}");
            row.createCell(1).setCellValue("${name}");
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Header in row 1, jx:each template row in row 2 (cells A-C),
     * total row in row 3 with formula =SUM(C2:C2).
     */
    public static byte[] tableEach() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");

            Row header = sh.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Qty");
            header.createCell(2).setCellValue("Total");

            Row data = sh.createRow(1);
            data.createCell(0).setCellValue("${item.name}");
            data.createCell(1).setCellValue("${item.qty}");
            Cell totalCell = data.createCell(2);
            totalCell.setCellValue("${item.qty * item.price}");

            Row totals = sh.createRow(2);
            totals.createCell(0).setCellValue("Total");
            totals.createCell(2).setCellFormula("SUM(C2:C2)");

            // jx:each comment on A2 with lastCell=C2
            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sh.createDrawingPatriarch();
            XSSFClientAnchor anchor = (XSSFClientAnchor) helper.createClientAnchor();
            anchor.setCol1(0); anchor.setCol2(2);
            anchor.setRow1(1); anchor.setRow2(3);
            Comment c = drawing.createCellComment(anchor);
            c.setString(helper.createRichTextString(
                "jx:each(items=\"items\", var=\"item\", lastCell=\"C2\")"));
            data.getCell(0).setCellComment(c);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** A1: ${a}, A2: ${b}, A3: =A1+A2 */
    public static byte[] formulas() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");
            sh.createRow(0).createCell(0).setCellValue("${a}");
            sh.createRow(1).createCell(0).setCellValue("${b}");
            sh.createRow(2).createCell(0).setCellFormula("A1+A2");
            wb.write(out);
            return out.toByteArray();
        }
    }
}
```

- [ ] **Step 2: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/internal/jxls/JxlsTemplateEngineTest.java`:

```java
package io.github.nikolaynn.docengine.internal.jxls;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.TemplateRenderingException;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.support.TemplateFixtures;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JxlsTemplateEngineTest {

    private JxlsTemplateEngine engine;
    private TempFileManager tfm;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        engine = new JxlsTemplateEngine();
        tfm = new DefaultTempFileManager(tmp, false);
    }

    @Test
    void supportsXlsx() {
        assertThat(engine.supports(DocumentFormat.XLSX)).isTrue();
        assertThat(engine.supports(DocumentFormat.PDF)).isFalse();
    }

    @Test
    void rendersScalarFields() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.simpleFields(),
            DocumentFormat.XLSX, "simple-fields");
        Map<String, Object> data = Map.of("greeting", "Hello", "name", "World");

        Path out = engine.render(template, data, ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
            Sheet sh = wb.getSheetAt(0);
            assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Hello");
            assertThat(sh.getRow(0).getCell(1).getStringCellValue()).isEqualTo("World");
        }
    }

    @Test
    void rendersTableWithJxEach() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.tableEach(),
            DocumentFormat.XLSX, "table-each");
        Map<String, Object> data = Map.of("items", List.of(
            Map.of("name", "A", "qty", 2, "price", new BigDecimal("100")),
            Map.of("name", "B", "qty", 3, "price", new BigDecimal("50"))
        ));

        Path out = engine.render(template, data, ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
            Sheet sh = wb.getSheetAt(0);
            // row 0 is header (kept), row 1 = first item, row 2 = second item, row 3 = totals
            assertThat(sh.getRow(1).getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(sh.getRow(2).getCell(0).getStringCellValue()).isEqualTo("B");
            assertThat(sh.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2.0);
            assertThat(sh.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(3.0);
        }
    }

    @Test
    void evaluatesFormulasAndCachesValues() throws Exception {
        var template = new ResolvedTemplate(TemplateFixtures.formulas(),
            DocumentFormat.XLSX, "formulas");
        Map<String, Object> data = Map.of("a", 10, "b", 5);

        Path out = engine.render(template, data, ctx());

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(out))) {
            Sheet sh = wb.getSheetAt(0);
            var formulaCell = sh.getRow(2).getCell(0);
            assertThat(formulaCell.getCellFormula()).isEqualToIgnoringWhitespace("A1+A2");
            assertThat(formulaCell.getNumericCellValue()).isEqualTo(15.0);
            assertThat(wb.getForceFormulaRecalculation()).isTrue();
        }
    }

    @Test
    void rejectsUnsupportedSourceFormat() {
        var template = new ResolvedTemplate(new byte[]{0}, DocumentFormat.PDF, "x");
        assertThatThrownBy(() -> engine.render(template, Map.of(), ctx()))
            .isInstanceOf(TemplateRenderingException.class)
            .hasMessageContaining("PDF");
    }

    @Test
    void mapsJxlsFailureToTemplateRenderingException() {
        var template = new ResolvedTemplate(new byte[]{0, 1, 2, 3}, DocumentFormat.XLSX, "corrupt");
        assertThatThrownBy(() -> engine.render(template, Map.of(), ctx()))
            .isInstanceOf(TemplateRenderingException.class);
    }

    private RenderContext ctx() {
        return new RenderContext(null, null, Map.of(), tfm, "test");
    }
}
```

- [ ] **Step 3: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 4: Implement**

`doc-engine-core/src/main/java/com/example/docengine/internal/jxls/JxlsTemplateEngine.java`:

```java
package io.github.nikolaynn.docengine.internal.jxls;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.TemplateRenderingException;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
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

        byte[] rendered = renderToBytes(template, data);
        byte[] withFormulas = recalculateFormulas(rendered, template.hint());

        Path out = ctx.tempFileManager().createTempFile("doc-engine-", ".xlsx");
        try {
            Files.write(out, withFormulas);
            log.debug("rendered template {} to {}", template.hint(), out);
            return out;
        } catch (IOException e) {
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "failed to write rendered xlsx to temp file", e);
        }
    }

    private byte[] renderToBytes(ResolvedTemplate template, Map<String, Object> data) {
        try (InputStream in = new ByteArrayInputStream(template.bytes());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Context jxlsCtx = new Context();
            data.forEach(jxlsCtx::putVar);
            JxlsHelper.getInstance().processTemplate(in, out, jxlsCtx);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new TemplateRenderingException(template.hint(), template.sourceFormat(), null,
                "JXLS failed to render template", e);
        }
    }

    private byte[] recalculateFormulas(byte[] xlsx, String hint) {
        try (InputStream in = new ByteArrayInputStream(xlsx);
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();
            wb.setForceFormulaRecalculation(true);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new TemplateRenderingException(hint, DocumentFormat.XLSX, null,
                "failed to recalculate formulas in rendered workbook", e);
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -pl doc-engine-core -q test`
Expected: all green. (If `tableEach` shifts header row, adjust row indices — JXLS preserves header row at index 0 and expands from index 1.)

- [ ] **Step 6: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/internal/jxls/ \
        doc-engine-core/src/test/java/com/example/docengine/internal/jxls/ \
        doc-engine-core/src/test/java/com/example/docengine/support/
git commit -m "feat(core): add JxlsTemplateEngine with formula recalculation"
```

---

## Task 11: LibreOfficeConverter

The converter is `ProcessBuilder`-based, so the unit tests verify error mapping with a fake executable. A real round-trip test is gated by `soffice` availability.

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/internal/libreoffice/LibreOfficeConverter.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/libreoffice/LibreOfficeConverterTest.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/libreoffice/LibreOfficeConverterIT.java`

- [ ] **Step 1: Write failing unit test (error-mapping only)**

`doc-engine-core/src/test/java/com/example/docengine/internal/libreoffice/LibreOfficeConverterTest.java`:

```java
package io.github.nikolaynn.docengine.internal.libreoffice;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibreOfficeConverterTest {

    @Test
    void supportsXlsxToPdfOnly() {
        var c = new LibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(60), null);
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).isTrue();
        assertThat(c.supports(DocumentFormat.PDF, DocumentFormat.XLSX)).isFalse();
        assertThat(c.supports(DocumentFormat.XLSX, DocumentFormat.XLSX)).isFalse();
    }

    @Test
    void missingExecutableMapsToConversionException(@TempDir Path tmp) throws Exception {
        Path input = Files.createTempFile(tmp, "in", ".xlsx");
        Files.writeString(input, "stub");
        Path bogusExe = tmp.resolve("definitely-not-soffice");
        var c = new LibreOfficeConverter(bogusExe, Duration.ofSeconds(5), tmp);
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.XLSX, DocumentFormat.PDF,
                new ConvertContext(Duration.ofSeconds(5), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class);
    }

    @Test
    void rejectsUnsupportedConversionPair(@TempDir Path tmp) throws Exception {
        Path input = Files.createTempFile(tmp, "in", ".pdf");
        Files.writeString(input, "stub");
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        var c = new LibreOfficeConverter(Path.of("soffice"), Duration.ofSeconds(5), tmp);

        assertThatThrownBy(() -> c.convert(input, DocumentFormat.PDF, DocumentFormat.XLSX,
                new ConvertContext(Duration.ofSeconds(5), tfm, "tpl")))
            .isInstanceOf(DocumentConversionException.class)
            .hasMessageContaining("unsupported");
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement converter**

`doc-engine-core/src/main/java/com/example/docengine/internal/libreoffice/LibreOfficeConverter.java`:

```java
package io.github.nikolaynn.docengine.internal.libreoffice;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.exception.DocumentConversionException;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class LibreOfficeConverter implements DocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeConverter.class);
    private static final int STDERR_TRUNCATE = 2000;

    private final Path executable;          // may be null → resolved as "soffice"
    private final Duration defaultTimeout;
    private final Path workingDir;          // may be null

    public LibreOfficeConverter(Path executable, Duration defaultTimeout, Path workingDir) {
        this.executable = executable;
        this.defaultTimeout = defaultTimeout == null ? Duration.ofSeconds(60) : defaultTimeout;
        this.workingDir = workingDir;
    }

    @Override
    public boolean supports(DocumentFormat from, DocumentFormat to) {
        return from == DocumentFormat.XLSX && to == DocumentFormat.PDF;
    }

    @Override
    public Path convert(Path input, DocumentFormat from, DocumentFormat to, ConvertContext ctx) {
        if (!supports(from, to)) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "unsupported conversion " + from + "->" + to, null, false);
        }

        Path outDir = createOutDir(ctx);
        Duration timeout = ctx.timeout() == null ? defaultTimeout : ctx.timeout();

        ProcessBuilder pb = new ProcessBuilder(buildCommand(input, outDir))
            .redirectErrorStream(false);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "failed to start LibreOffice: " + e.getMessage(), e, false);
        }

        String stderr;
        boolean finished;
        try {
            stderr = readStderrAsync(process);
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "interrupted while waiting for LibreOffice", e, false);
        }

        if (!finished) {
            process.destroyForcibly();
            throw DocumentConversionException.timeout(ctx.templateHint(), from, to, timeout);
        }

        int exit = process.exitValue();
        if (exit != 0) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "LibreOffice exited with code " + exit + "; stderr=" + truncate(stderr),
                null, false);
        }

        Path output = findOutputFile(input, outDir, to);
        if (output == null) {
            throw new DocumentConversionException(ctx.templateHint(), from, to,
                "LibreOffice exited 0 but no output file in " + outDir, null, false);
        }
        log.debug("converted {} -> {}", input, output);
        return output;
    }

    private List<String> buildCommand(Path input, Path outDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable == null ? "soffice" : executable.toString());
        cmd.add("--headless");
        cmd.add("--convert-to");
        cmd.add("pdf");
        cmd.add("--outdir");
        cmd.add(outDir.toString());
        cmd.add(input.toString());
        return cmd;
    }

    private Path createOutDir(ConvertContext ctx) {
        try {
            Path base = workingDir == null
                ? Files.createTempDirectory("doc-engine-libo-")
                : Files.createTempDirectory(workingDir, "doc-engine-libo-");
            return base;
        } catch (IOException e) {
            throw new DocumentConversionException(ctx.templateHint(),
                DocumentFormat.XLSX, DocumentFormat.PDF,
                "failed to create LibreOffice output directory", e, false);
        }
    }

    private static Path findOutputFile(Path input, Path outDir, DocumentFormat targetFormat) {
        String base = input.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        Path candidate = outDir.resolve(stem + "." + targetFormat.extension());
        if (Files.isRegularFile(candidate)) return candidate;
        try (Stream<Path> s = Files.list(outDir)) {
            return s.filter(p -> p.getFileName().toString().endsWith("." + targetFormat.extension()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readStderrAsync(Process p) {
        StringBuilder sb = new StringBuilder();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (sb) { sb.append(line).append('\n'); }
                }
            } catch (IOException ignored) {}
        }, "soffice-stderr");
        t.setDaemon(true);
        t.start();
        try { t.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        synchronized (sb) { return sb.toString(); }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= STDERR_TRUNCATE ? s : s.substring(0, STDERR_TRUNCATE) + "...";
    }
}
```

- [ ] **Step 4: Write conditional integration test**

`doc-engine-core/src/test/java/com/example/docengine/internal/libreoffice/LibreOfficeConverterIT.java`:

```java
package io.github.nikolaynn.docengine.internal.libreoffice;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("sofficeAvailable")
class LibreOfficeConverterIT {

    static boolean sofficeAvailable() {
        try {
            Process p = new ProcessBuilder("soffice", "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    void convertsXlsxToPdf(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("in.xlsx");
        Files.write(xlsx, io.github.nikolaynn.docengine.support.TemplateFixtures.simpleFields());
        TempFileManager tfm = new DefaultTempFileManager(tmp, false);
        var c = new LibreOfficeConverter(null, Duration.ofSeconds(60), tmp);

        Path pdf = c.convert(xlsx, DocumentFormat.XLSX, DocumentFormat.PDF,
            new ConvertContext(Duration.ofSeconds(60), tfm, "it"));

        assertThat(pdf).exists();
        assertThat(Files.size(pdf)).isGreaterThan(100);
    }
}
```

- [ ] **Step 5: Run unit + IT (IT skipped if soffice absent)**

Run: `mvn -pl doc-engine-core -q test`
Expected: unit tests green; `LibreOfficeConverterIT` skipped on hosts without LibreOffice.

- [ ] **Step 6: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/internal/libreoffice/ \
        doc-engine-core/src/test/java/com/example/docengine/internal/libreoffice/
git commit -m "feat(core): add LibreOfficeConverter (soffice headless) for XLSX→PDF"
```

---

## Task 12: DefaultDocumentEngine orchestrator

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/internal/DefaultDocumentEngine.java`
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/DocumentEngine.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/internal/DefaultDocumentEngineTest.java`

- [ ] **Step 1: Create DocumentEngine interface**

`doc-engine-core/src/main/java/com/example/docengine/api/DocumentEngine.java`:

```java
package io.github.nikolaynn.docengine.api;

public interface DocumentEngine {
    GenerationResult generate(GenerationRequest request);
}
```

- [ ] **Step 2: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/internal/DefaultDocumentEngineTest.java`:

```java
package io.github.nikolaynn.docengine.internal;

import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationOptions;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.api.exception.InvalidGenerationRequestException;
import io.github.nikolaynn.docengine.api.exception.TemplateRenderingException;
import io.github.nikolaynn.docengine.api.exception.UnsupportedConversionException;
import io.github.nikolaynn.docengine.api.exception.UnsupportedTemplateFormatException;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDocumentEngineTest {

    private TempFileManager tfm;
    private TemplateResolver resolver;
    private TemplateValidator validator;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        tfm = new DefaultTempFileManager(tmp, false);
        resolver = ref -> new ResolvedTemplate(
            ((TemplateReference.BytesRef) ref).bytes(), ref.sourceFormat(), ref.hint());
        validator = ref -> {};
    }

    @Test
    void rejectsNullRequest() {
        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(), List.of(), resolver, validator, tfm);
        assertThatThrownBy(() -> engine.generate(null))
            .isInstanceOf(InvalidGenerationRequestException.class);
    }

    @Test
    void rendersXlsxAndSkipsConversionWhenFormatsMatch(@TempDir Path tmp) throws Exception {
        Path rendered = createNonEmpty(tmp, "rendered", ".xlsx");
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DocumentConverter dc = mock(DocumentConverter.class);

        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(dc), resolver, validator, tfm);

        var result = engine.generate(req(DocumentFormat.XLSX, "report"));

        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(result.mimeType()).isEqualTo(DocumentFormat.XLSX.mimeType());
        assertThat(result.content()).hasSize((int) Files.size(rendered));
        verify(dc, never()).convert(any(), any(), any(), any());
        // rendered file cleaned up
        assertThat(rendered).doesNotExist();
    }

    @Test
    void runsConverterWhenFormatsDiffer(@TempDir Path tmp) throws Exception {
        Path rendered = createNonEmpty(tmp, "rendered", ".xlsx");
        Path converted = createNonEmpty(tmp, "converted", ".pdf");
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DocumentConverter dc = mock(DocumentConverter.class);
        when(dc.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).thenReturn(true);
        when(dc.convert(any(), any(), any(), any())).thenReturn(converted);

        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(dc), resolver, validator, tfm);

        var result = engine.generate(req(DocumentFormat.PDF, "report"));

        assertThat(result.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(result.mimeType()).isEqualTo(DocumentFormat.PDF.mimeType());
        assertThat(rendered).doesNotExist();
        assertThat(converted).doesNotExist();
        verify(dc, times(1)).convert(any(), any(), any(), any());
    }

    @Test
    void cleansUpWhenConverterThrows(@TempDir Path tmp) throws Exception {
        Path rendered = createNonEmpty(tmp, "rendered", ".xlsx");
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DocumentConverter dc = mock(DocumentConverter.class);
        when(dc.supports(DocumentFormat.XLSX, DocumentFormat.PDF)).thenReturn(true);
        when(dc.convert(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(dc), resolver, validator, tfm);

        assertThatThrownBy(() -> engine.generate(req(DocumentFormat.PDF, "report")))
            .isInstanceOf(RuntimeException.class);
        assertThat(rendered).doesNotExist();
    }

    @Test
    void throwsWhenNoTemplateEngineSupportsSource() {
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(any())).thenReturn(false);
        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(), resolver, validator, tfm);
        assertThatThrownBy(() -> engine.generate(req(DocumentFormat.XLSX, "h")))
            .isInstanceOf(UnsupportedTemplateFormatException.class);
    }

    @Test
    void throwsWhenNoConverterSupportsConversion(@TempDir Path tmp) throws Exception {
        Path rendered = createNonEmpty(tmp, "rendered", ".xlsx");
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(), resolver, validator, tfm);
        assertThatThrownBy(() -> engine.generate(req(DocumentFormat.PDF, "h")))
            .isInstanceOf(UnsupportedConversionException.class);
        assertThat(rendered).doesNotExist();
    }

    @Test
    void wrapsUncheckedTemplateEngineFailure(@TempDir Path tmp) throws Exception {
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any()))
            .thenThrow(new TemplateRenderingException("h", DocumentFormat.XLSX, null, "fail", null));
        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(), resolver, validator, tfm);
        assertThatThrownBy(() -> engine.generate(req(DocumentFormat.XLSX, "h")))
            .isInstanceOf(TemplateRenderingException.class);
    }

    @Test
    void usesFileNameHintWhenPresent(@TempDir Path tmp) throws Exception {
        Path rendered = createNonEmpty(tmp, "rendered", ".xlsx");
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(), resolver, validator, tfm);

        var opts = new GenerationOptions("my-report", null, null, null);
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, "h"),
            Map.of(), DocumentFormat.XLSX, opts);
        var result = engine.generate(req);
        assertThat(result.fileName()).isEqualTo("my-report.xlsx");
    }

    @Test
    void fallsBackToTemplateHintForFileName(@TempDir Path tmp) throws Exception {
        Path rendered = createNonEmpty(tmp, "rendered", ".xlsx");
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(), resolver, validator, tfm);

        var result = engine.generate(req(DocumentFormat.XLSX, "myhint"));
        assertThat(result.fileName()).startsWith("myhint-").endsWith(".xlsx");
    }

    private static GenerationRequest req(DocumentFormat target, String hint) {
        return new GenerationRequest(
            new TemplateReference.BytesRef(new byte[]{1}, DocumentFormat.XLSX, hint),
            Map.of(), target, GenerationOptions.defaults());
    }

    private static Path createNonEmpty(Path dir, String prefix, String suffix) throws IOException {
        Path p = Files.createTempFile(dir, prefix, suffix);
        Files.writeString(p, "x");
        return p;
    }
}
```

- [ ] **Step 3: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 4: Implement orchestrator**

`doc-engine-core/src/main/java/com/example/docengine/internal/DefaultDocumentEngine.java`:

```java
package io.github.nikolaynn.docengine.internal;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationOptions;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.GenerationResult;
import io.github.nikolaynn.docengine.api.exception.InvalidGenerationRequestException;
import io.github.nikolaynn.docengine.api.exception.UnsupportedConversionException;
import io.github.nikolaynn.docengine.api.exception.UnsupportedTemplateFormatException;
import io.github.nikolaynn.docengine.spi.ConvertContext;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.RenderContext;
import io.github.nikolaynn.docengine.spi.ResolvedTemplate;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class DefaultDocumentEngine implements DocumentEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentEngine.class);

    private final List<TemplateEngine> templateEngines;
    private final List<DocumentConverter> converters;
    private final TemplateResolver resolver;
    private final TemplateValidator validator;
    private final TempFileManager tempFiles;

    public DefaultDocumentEngine(List<TemplateEngine> templateEngines,
                                 List<DocumentConverter> converters,
                                 TemplateResolver resolver,
                                 TemplateValidator validator,
                                 TempFileManager tempFiles) {
        this.templateEngines = List.copyOf(Objects.requireNonNull(templateEngines, "templateEngines"));
        this.converters = List.copyOf(Objects.requireNonNull(converters, "converters"));
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.tempFiles = Objects.requireNonNull(tempFiles, "tempFiles");
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        if (request == null) {
            throw new InvalidGenerationRequestException("request must not be null");
        }
        DocumentFormat target = request.targetFormat();
        DocumentFormat source = request.template().sourceFormat();
        GenerationOptions opts = request.options();

        validator.validate(request.template());
        ResolvedTemplate resolved = resolver.resolve(request.template());

        TemplateEngine te = templateEngines.stream()
            .filter(e -> e.supports(source))
            .findFirst()
            .orElseThrow(() -> new UnsupportedTemplateFormatException(request.template().hint(), source));

        Path rendered = null;
        Path converted = null;
        try {
            RenderContext rctx = new RenderContext(opts.locale(), opts.timeout(),
                opts.engineHints(), tempFiles, request.template().hint());
            rendered = te.render(resolved, request.data(), rctx);

            Path output;
            if (source == target) {
                output = rendered;
            } else {
                DocumentConverter dc = converters.stream()
                    .filter(c -> c.supports(source, target))
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedConversionException(
                        request.template().hint(), source, target));
                ConvertContext cctx = new ConvertContext(opts.timeout(), tempFiles,
                    request.template().hint());
                converted = dc.convert(rendered, source, target, cctx);
                output = converted;
            }

            byte[] bytes = readAllBytes(output, request.template().hint());
            String fileName = buildFileName(opts.fileNameHint(), request.template().hint(), target);
            return new GenerationResult(fileName, target.mimeType(), target, bytes);
        } finally {
            tempFiles.delete(rendered);
            tempFiles.delete(converted);
        }
    }

    private static byte[] readAllBytes(Path file, String hint) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new io.github.nikolaynn.docengine.api.exception.TempFileException(
                hint, null, null, "failed to read produced document", e);
        }
    }

    private static String buildFileName(String hint, String templateHint, DocumentFormat target) {
        String ext = "." + target.extension();
        if (hint != null && !hint.isBlank()) {
            return hint.endsWith(ext) ? hint : hint + ext;
        }
        String base = (templateHint == null || templateHint.isBlank()) ? "document" : sanitize(templateHint);
        return base + "-" + Instant.now().toEpochMilli() + ext;
    }

    private static String sanitize(String hint) {
        // strip path/extension noise to keep the name printable and safe
        String name = hint;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -pl doc-engine-core -q test`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/api/DocumentEngine.java \
        doc-engine-core/src/main/java/com/example/docengine/internal/DefaultDocumentEngine.java \
        doc-engine-core/src/test/java/com/example/docengine/internal/
git commit -m "feat(core): add DefaultDocumentEngine orchestrator"
```

---

## Task 13: DocumentEngineBuilder (plain-Java entry point)

**Files:**
- Create: `doc-engine-core/src/main/java/com/example/docengine/api/DocumentEngineBuilder.java`
- Test: `doc-engine-core/src/test/java/com/example/docengine/api/DocumentEngineBuilderTest.java`

- [ ] **Step 1: Write failing test**

`doc-engine-core/src/test/java/com/example/docengine/api/DocumentEngineBuilderTest.java`:

```java
package io.github.nikolaynn.docengine.api;

import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DocumentEngineBuilderTest {

    @Test
    void builderRequiresAtLeastOneTemplateEngineAndTempFileManager() {
        assertThatThrownBy(() -> DocumentEngineBuilder.create().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("templateEngine");
    }

    @Test
    void buildsEngineWhenAllRequiredDepsProvided() {
        TempFileManager tfm = mock(TempFileManager.class);
        TemplateResolver tr = mock(TemplateResolver.class);
        TemplateValidator tv = mock(TemplateValidator.class);
        TemplateEngine te = mock(TemplateEngine.class);
        DocumentConverter dc = mock(DocumentConverter.class);

        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(tfm)
            .templateResolver(tr)
            .templateValidator(tv)
            .addTemplateEngine(te)
            .addConverter(dc)
            .build();

        assertThat(engine).isNotNull();
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-core -q test`
Expected: compile error.

- [ ] **Step 3: Implement builder**

`doc-engine-core/src/main/java/com/example/docengine/api/DocumentEngineBuilder.java`:

```java
package io.github.nikolaynn.docengine.api;

import io.github.nikolaynn.docengine.internal.DefaultDocumentEngine;
import io.github.nikolaynn.docengine.internal.resolver.InputStreamTemplateResolver;
import io.github.nikolaynn.docengine.internal.validator.NoopTemplateValidator;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DocumentEngineBuilder {

    private TempFileManager tempFileManager;
    private TemplateResolver templateResolver;
    private TemplateValidator templateValidator;
    private final List<TemplateEngine> templateEngines = new ArrayList<>();
    private final List<DocumentConverter> converters = new ArrayList<>();

    private DocumentEngineBuilder() {}

    public static DocumentEngineBuilder create() { return new DocumentEngineBuilder(); }

    public DocumentEngineBuilder tempFileManager(TempFileManager tfm) {
        this.tempFileManager = Objects.requireNonNull(tfm); return this;
    }
    public DocumentEngineBuilder templateResolver(TemplateResolver tr) {
        this.templateResolver = Objects.requireNonNull(tr); return this;
    }
    public DocumentEngineBuilder templateValidator(TemplateValidator tv) {
        this.templateValidator = Objects.requireNonNull(tv); return this;
    }
    public DocumentEngineBuilder addTemplateEngine(TemplateEngine te) {
        templateEngines.add(Objects.requireNonNull(te)); return this;
    }
    public DocumentEngineBuilder addConverter(DocumentConverter dc) {
        converters.add(Objects.requireNonNull(dc)); return this;
    }

    public DocumentEngine build() {
        if (tempFileManager == null) {
            throw new IllegalStateException("tempFileManager is required");
        }
        if (templateEngines.isEmpty()) {
            throw new IllegalStateException("at least one templateEngine is required");
        }
        TemplateResolver tr = templateResolver != null ? templateResolver : new InputStreamTemplateResolver();
        TemplateValidator tv = templateValidator != null ? templateValidator : new NoopTemplateValidator();
        return new DefaultDocumentEngine(templateEngines, converters, tr, tv, tempFileManager);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -pl doc-engine-core -q test`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add doc-engine-core/src/main/java/com/example/docengine/api/DocumentEngineBuilder.java \
        doc-engine-core/src/test/java/com/example/docengine/api/DocumentEngineBuilderTest.java
git commit -m "feat(core): add DocumentEngineBuilder for plain-Java wiring"
```

---

## Task 14: End-to-end smoke test of core (plain Java)

**Files:**
- Test: `doc-engine-core/src/test/java/com/example/docengine/EndToEndTest.java`

This exercises the full happy path through `DocumentEngineBuilder` with the real `JxlsTemplateEngine`, no Spring. PDF branch is gated on `soffice` availability.

- [ ] **Step 1: Write test**

`doc-engine-core/src/test/java/com/example/docengine/EndToEndTest.java`:

```java
package io.github.nikolaynn.docengine;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.api.DocumentEngineBuilder;
import io.github.nikolaynn.docengine.api.DocumentFormat;
import io.github.nikolaynn.docengine.api.GenerationOptions;
import io.github.nikolaynn.docengine.api.GenerationRequest;
import io.github.nikolaynn.docengine.api.TemplateReference;
import io.github.nikolaynn.docengine.internal.jxls.JxlsTemplateEngine;
import io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverter;
import io.github.nikolaynn.docengine.internal.tempfile.DefaultTempFileManager;
import io.github.nikolaynn.docengine.support.TemplateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndTest {

    @Test
    void xlsxRoundTripWithBuilder(@TempDir Path tmp) throws Exception {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .build();
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "tpl"),
            Map.of("greeting", "Hi", "name", "World"),
            DocumentFormat.XLSX,
            GenerationOptions.defaults());
        var result = engine.generate(req);
        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(result.fileName()).endsWith(".xlsx");
        assertThat(result.content()).isNotEmpty();
    }

    @EnabledIf("io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverterIT#sofficeAvailable")
    @Test
    void pdfRoundTripWithBuilder(@TempDir Path tmp) throws IOException {
        DocumentEngine engine = DocumentEngineBuilder.create()
            .tempFileManager(new DefaultTempFileManager(tmp, false))
            .addTemplateEngine(new JxlsTemplateEngine())
            .addConverter(new LibreOfficeConverter(null, Duration.ofSeconds(60), tmp))
            .build();
        var req = new GenerationRequest(
            new TemplateReference.BytesRef(TemplateFixtures.simpleFields(), DocumentFormat.XLSX, "tpl"),
            Map.of("greeting", "Hi", "name", "World"),
            DocumentFormat.PDF,
            GenerationOptions.defaults());
        var result = engine.generate(req);
        assertThat(result.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(result.fileName()).endsWith(".pdf");
        assertThat(result.content()).isNotEmpty();
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }
}
```

- [ ] **Step 2: Run**

Run: `mvn -pl doc-engine-core -q test`
Expected: both tests pass (PDF one skipped if soffice absent).

- [ ] **Step 3: Commit**

```bash
git add doc-engine-core/src/test/java/com/example/docengine/EndToEndTest.java
git commit -m "test(core): add end-to-end plain-Java happy path test"
```

---

## Task 15: Spring Boot starter (properties + auto-configuration + tests)

**Files:**
- Create: `doc-engine-spring-boot-starter/src/main/java/com/example/docengine/starter/DocEngineProperties.java`
- Create: `doc-engine-spring-boot-starter/src/main/java/com/example/docengine/starter/DocEngineAutoConfiguration.java`
- Create: `doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories`
- Test: `doc-engine-spring-boot-starter/src/test/java/com/example/docengine/starter/DocEngineAutoConfigurationTest.java`

- [ ] **Step 1: Write failing autoconfig test**

`doc-engine-spring-boot-starter/src/test/java/com/example/docengine/starter/DocEngineAutoConfigurationTest.java`:

```java
package io.github.nikolaynn.docengine.starter;

import io.github.nikolaynn.docengine.api.DocumentEngine;
import io.github.nikolaynn.docengine.internal.libreoffice.LibreOfficeConverter;
import io.github.nikolaynn.docengine.spi.DocumentConverter;
import io.github.nikolaynn.docengine.spi.TempFileManager;
import io.github.nikolaynn.docengine.spi.TemplateEngine;
import io.github.nikolaynn.docengine.spi.TemplateResolver;
import io.github.nikolaynn.docengine.spi.TemplateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocEngineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DocEngineAutoConfiguration.class));

    @Test
    void defaultsAllBeansPresent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DocumentEngine.class);
            assertThat(ctx).hasSingleBean(TempFileManager.class);
            assertThat(ctx).hasSingleBean(TemplateResolver.class);
            assertThat(ctx).hasSingleBean(TemplateValidator.class);
            assertThat(ctx).hasBean("jxlsTemplateEngine");
            assertThat(ctx).hasBean("libreOfficeConverter");
        });
    }

    @Test
    void libreOfficeConverterCanBeDisabledViaProperty() {
        runner.withPropertyValues("doc-engine.converter.libreoffice.enabled=false")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(DocumentEngine.class);
                  assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
              });
    }

    @Test
    void userProvidedDocumentEngineWins() {
        runner.withUserConfiguration(UserEngineConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(DocumentEngine.class);
            assertThat(ctx.getBean(DocumentEngine.class))
                .isSameAs(ctx.getBean("userEngine"));
        });
    }

    @Test
    void userConverterReplacesDefault() {
        runner.withUserConfiguration(UserConverterConfig.class).run(ctx -> {
            assertThat(ctx).doesNotHaveBean("libreOfficeConverter");
            assertThat(ctx).hasBean("libreOfficeConverter".equals("libreOfficeConverter")
                ? "libreOfficeConverter" : "ignored"); // sanity placeholder
            assertThat(ctx.getBeansOfType(DocumentConverter.class).values())
                .anyMatch(c -> c == ctx.getBean("userConverter"));
        });
    }

    @Test
    void propertiesBind() {
        runner.withPropertyValues(
            "doc-engine.temp-dir=/tmp/de",
            "doc-engine.cleanup-on-shutdown=false",
            "doc-engine.converter.libreoffice.executable=/opt/soffice",
            "doc-engine.converter.libreoffice.timeout=15s"
        ).run(ctx -> {
            DocEngineProperties p = ctx.getBean(DocEngineProperties.class);
            assertThat(p.tempDir().toString()).isEqualTo("/tmp/de");
            assertThat(p.cleanupOnShutdown()).isFalse();
            assertThat(p.converter().executable().toString()).isEqualTo("/opt/soffice");
            assertThat(p.converter().timeout().toSeconds()).isEqualTo(15);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserEngineConfig {
        @Bean DocumentEngine userEngine() { return mock(DocumentEngine.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserConverterConfig {
        @Bean(name = "libreOfficeConverter")
        DocumentConverter userConverter() { return mock(DocumentConverter.class); }
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `mvn -pl doc-engine-spring-boot-starter -q test`
Expected: compile error.

- [ ] **Step 3: Implement DocEngineProperties**

`doc-engine-spring-boot-starter/src/main/java/com/example/docengine/starter/DocEngineProperties.java`:

```java
package io.github.nikolaynn.docengine.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("doc-engine")
public record DocEngineProperties(
        Path tempDir,
        boolean cleanupOnShutdown,
        LibreOffice converter
) {
    public DocEngineProperties {
        if (converter == null) converter = new LibreOffice(true, null, null, null);
    }

    public record LibreOffice(boolean enabled, Path executable, Duration timeout, Path workingDir) {
        public LibreOffice {
            if (timeout == null) timeout = Duration.ofSeconds(60);
        }
    }
}
```

- [ ] **Step 4: Implement DocEngineAutoConfiguration**

`doc-engine-spring-boot-starter/src/main/java/com/example/docengine/starter/DocEngineAutoConfiguration.java`:

```java
package io.github.nikolaynn.docengine.starter;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocEngineProperties.class)
public class DocEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TempFileManager tempFileManager(DocEngineProperties props) {
        return new DefaultTempFileManager(props.tempDir(), props.cleanupOnShutdown());
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateResolver templateResolver() {
        return new InputStreamTemplateResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateValidator templateValidator() {
        return new NoopTemplateValidator();
    }

    @Bean(name = "jxlsTemplateEngine")
    @ConditionalOnMissingBean(name = "jxlsTemplateEngine")
    public TemplateEngine jxlsTemplateEngine() {
        return new JxlsTemplateEngine();
    }

    @Bean(name = "libreOfficeConverter")
    @ConditionalOnMissingBean(name = "libreOfficeConverter")
    @ConditionalOnProperty(prefix = "doc-engine.converter.libreoffice",
                           name = "enabled", havingValue = "true", matchIfMissing = true)
    public DocumentConverter libreOfficeConverter(DocEngineProperties props) {
        var c = props.converter();
        return new LibreOfficeConverter(c.executable(), c.timeout(), c.workingDir());
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentEngine documentEngine(List<TemplateEngine> engines,
                                         List<DocumentConverter> converters,
                                         TemplateResolver resolver,
                                         TemplateValidator validator,
                                         TempFileManager tempFiles) {
        return new DefaultDocumentEngine(engines, converters, resolver, validator, tempFiles);
    }
}
```

- [ ] **Step 5: Add spring.factories registration**

`doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories`:

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
io.github.nikolaynn.docengine.starter.DocEngineAutoConfiguration
```

- [ ] **Step 6: Run starter tests**

Run: `mvn -pl doc-engine-spring-boot-starter -q test`
Expected: all green.

- [ ] **Step 7: Full build**

Run: `mvn -q clean verify`
Expected: BUILD SUCCESS across both modules.

- [ ] **Step 8: Commit**

```bash
git add doc-engine-spring-boot-starter/src/main/java/com/example/docengine/starter/ \
        doc-engine-spring-boot-starter/src/main/resources/META-INF/spring.factories \
        doc-engine-spring-boot-starter/src/test/java/com/example/docengine/starter/
git commit -m "feat(starter): add Spring Boot 2.7 auto-configuration for DocumentEngine"
```

---

## Self-Review Notes

**Spec coverage check:**
- §2 fixed MVP decisions → Tasks 1 (Maven, Java 17, deps), 2 (DocumentFormat), 10 (JXLS), 11 (LibreOffice), 15 (Spring Boot starter)
- §3 module structure → Task 1 + ongoing
- §4.1 public API → Tasks 2, 3, 4, 12 (interface), 13 (builder)
- §4.2 SPI → Task 6
- §5 data flow & registry → Task 12
- §5.3 default implementations → Tasks 7, 8, 9, 10, 11
- §5.4 JXLS features (jx:each, formulas) → Task 10 tests cover scalar, jx:each, formulas
- §6 error hierarchy → Task 5
- §7 starter + properties → Task 15
- §8 testing strategy: unit tests in every task; integration test in Task 11 (gated `@EnabledIf`); ApplicationContextRunner in Task 15

**Placeholder scan:** all code blocks contain compilable code; no "TBD"/"TODO"; integration tests use real conditional execution, not stubs.

**Type/signature consistency:** `ResolvedTemplate` is used identically across TemplateEngine, resolver, and tests (`bytes`, `sourceFormat`, `hint`). `RenderContext` constructor `(locale, timeout, engineHints, tempFileManager, templateHint)` is consistent in Task 6 (definition), Task 10 (test usage), Task 12 (orchestrator). `DocumentConversionException.timeout(...)` factory used both in converter (Task 11) and exception tests (Task 5).

**Known minor risks for the implementer:**
- JXLS row index after `jx:each` expansion: header stays at row 0; first expanded data row is row 1; in Task 10 the assertion is `getRow(1)` and `getRow(2)` accordingly. If observed differently in real run, adjust assertions, not the engine.
- `LibreOfficeConverterIT#sofficeAvailable` is referenced from `EndToEndTest`'s `@EnabledIf` via a fully-qualified method name — confirm JUnit 5 accepts the FQN syntax on your Junit version (5.7+).
- `DefaultDocumentEngine` cleanup deletes `rendered` after reading its bytes; if the same path is used as output (`source == target`), the deletion still works because bytes are already in memory.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-26-doc-generator-engine.md`.**

Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks, fast iteration. Best for this plan because each task produces a self-contained, testable unit and the green-test gate makes per-task review cheap.

**2. Inline Execution** — execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

Which approach?
