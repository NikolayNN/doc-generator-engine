package com.example.docengine.internal;

import com.example.docengine.api.DocumentFormat;
import com.example.docengine.api.GenerationOptions;
import com.example.docengine.api.GenerationRequest;
import com.example.docengine.api.TemplateReference;
import com.example.docengine.api.exception.InvalidGenerationRequestException;
import com.example.docengine.api.exception.TemplateRenderingException;
import com.example.docengine.api.exception.UnsupportedConversionException;
import com.example.docengine.api.exception.UnsupportedTemplateFormatException;
import com.example.docengine.internal.tempfile.DefaultTempFileManager;
import com.example.docengine.spi.ConvertContext;
import com.example.docengine.spi.DocumentConverter;
import com.example.docengine.spi.RenderContext;
import com.example.docengine.spi.ResolvedTemplate;
import com.example.docengine.spi.TempFileManager;
import com.example.docengine.spi.TemplateEngine;
import com.example.docengine.spi.TemplateResolver;
import com.example.docengine.spi.TemplateValidator;
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
        int expectedSize = (int) Files.size(rendered);
        TemplateEngine te = mock(TemplateEngine.class);
        when(te.supports(DocumentFormat.XLSX)).thenReturn(true);
        when(te.render(any(), any(), any())).thenReturn(rendered);
        DocumentConverter dc = mock(DocumentConverter.class);

        DefaultDocumentEngine engine = new DefaultDocumentEngine(
            List.of(te), List.of(dc), resolver, validator, tfm);

        var result = engine.generate(req(DocumentFormat.XLSX, "report"));

        assertThat(result.format()).isEqualTo(DocumentFormat.XLSX);
        assertThat(result.mimeType()).isEqualTo(DocumentFormat.XLSX.mimeType());
        assertThat(result.content()).hasSize(expectedSize);
        verify(dc, never()).convert(any(), any(), any(), any());
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
