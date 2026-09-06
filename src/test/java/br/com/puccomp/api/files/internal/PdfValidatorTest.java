package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.files.FileUpload;
import br.com.puccomp.api.shared.exception.InvalidFileException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class PdfValidatorTest {
    private final PdfValidator validator = new PdfValidator(
            properties(20, DataSize.ofMegabytes(32), DataSize.ofMegabytes(256)));

    static byte[] pdf(Consumer<PDDocument> customize) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            customize.accept(document);
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    @DisplayName("aceita PDF real, incluindo nome com acento")
    void acceptsActualPdf() throws Exception {
        byte[] bytes = pdf(d -> { });
        var upload = upload("Currículo.pdf", "application/pdf", bytes);
        assertThat(validator.filename(upload)).isEqualTo("Currículo.pdf");
        assertThatCode(() -> validator.validate(validator.read(upload))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../cv.pdf", "C:\\cv.pdf", "cv.pdf.exe", "cv\r\n.pdf", ".pdf", "cv..pdf", "cv\".pdf"})
    @DisplayName("rejeita caminhos, extensões falsas e injeção de cabeçalho")
    void rejectsUnsafeFilename(String filename) {
        assertThatExceptionOfType(InvalidFileException.class)
                .isThrownBy(() -> validator.filename(upload(filename, "application/pdf", new byte[10])));
    }

    @Test
    @DisplayName("rejeita MIME falso, arquivo vazio e tamanho excedente ou divergente")
    void rejectsInvalidSizeAndMime() {
        assertThatExceptionOfType(InvalidFileException.class)
                .isThrownBy(() -> validator.filename(upload("cv.pdf", "image/png", new byte[10])));
        assertThatExceptionOfType(InvalidFileException.class)
                .isThrownBy(() -> validator.filename(upload("cv.pdf", "application/pdf", new byte[0])));
        assertThatExceptionOfType(InvalidFileException.class)
                .isThrownBy(() -> validator.filename(upload("cv.pdf", "application/pdf", new byte[PdfValidator.MAX_BYTES + 1])));
        var dishonest = new FileUpload("cv.pdf", "application/pdf", 10, () -> new ByteArrayInputStream(new byte[PdfValidator.MAX_BYTES + 1]));
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.read(dishonest));
    }

    @Test
    @DisplayName("assinatura PDF sozinha não torna um arquivo válido")
    void rejectsSpoofedAndTruncatedPdf() throws Exception {
        assertThatExceptionOfType(InvalidFileException.class)
                .isThrownBy(() -> validator.validate("%PDF-1.7\nfake\n%%EOF".getBytes()));
        byte[] valid = pdf(d -> { });
        assertThatExceptionOfType(InvalidFileException.class)
                .isThrownBy(() -> validator.read(upload("cv.pdf", "application/pdf",
                        java.util.Arrays.copyOf(valid, valid.length / 2))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AA", "JavaScript", "EmbeddedFiles", "XFA", "RichMediaContent"})
    @DisplayName("rejeita recursos ativos mesmo em objetos indiretos do PDF")
    void rejectsActiveContent(String key) throws Exception {
        byte[] bytes = pdf(d -> d.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName(key), new COSDictionary()));
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(bytes));
    }

    @Test
    @DisplayName("aceita AcroForm vazio e OpenAction que só aponta para uma página")
    void acceptsBenignFormAndOpenAction() throws Exception {
        byte[] bytes = pdf(d -> {
            var catalog = d.getDocumentCatalog().getCOSObject();
            catalog.setItem(COSName.ACRO_FORM, new COSDictionary());
            var destination = new COSArray();
            destination.add(COSName.getPDFName("Fit"));
            catalog.setItem(COSName.OPEN_ACTION, destination);
        });
        assertThatCode(() -> validator.validate(bytes)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("formulário e OpenAction continuam bloqueados quando carregam XFA ou script")
    void rejectsDangerousContentInsideFormAndOpenAction() throws Exception {
        byte[] xfa = pdf(d -> {
            var form = new COSDictionary();
            form.setItem(COSName.XFA, new COSArray());
            d.getDocumentCatalog().getCOSObject().setItem(COSName.ACRO_FORM, form);
        });
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(xfa));

        // Só o tipo da ação: prova que a checagem de /S pega o script sem depender da chave OpenAction.
        byte[] script = pdf(d -> {
            var action = new COSDictionary();
            action.setItem(COSName.S, COSName.getPDFName("JavaScript"));
            d.getDocumentCatalog().getCOSObject().setItem(COSName.OPEN_ACTION, action);
        });
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(script));
    }

    @Test
    @DisplayName("orçamento de inflação acompanha as páginas e para no teto absoluto")
    void scalesDecompressionBudgetWithPages() throws Exception {
        var properties = properties(20, DataSize.ofMegabytes(32), DataSize.ofMegabytes(256));
        assertThat(properties.decompressionBudget(1)).isEqualTo(DataSize.ofMegabytes(32).toBytes());
        assertThat(properties.decompressionBudget(20)).isEqualTo(DataSize.ofMegabytes(256).toBytes());

        byte[] bytes = pdf(d -> {
            try (var content = new PDPageContentStream(d, d.getPage(0))) {
                content.addRect(50, 50, 100, 100);
                content.fill();
            } catch (IOException e) { throw new RuntimeException(e); }
        });
        assertThatCode(() -> validator.validate(bytes)).doesNotThrowAnyException();
        var strict = new PdfValidator(properties(20, DataSize.ofBytes(1), DataSize.ofBytes(1)));
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> strict.validate(bytes));
    }

    @Test
    @DisplayName("rejeita PDF criptografado e mais páginas que o configurado")
    void rejectsEncryptionAndExcessPages() throws Exception {
        byte[] encrypted = pdf(d -> {
            try { d.protect(new StandardProtectionPolicy("owner", "", new AccessPermission())); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(encrypted));
        byte[] pages = pdf(d -> { for (int i = 0; i < 20; i++) d.addPage(new PDPage()); });
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(pages));
        var lenient = new PdfValidator(properties(21, DataSize.ofMegabytes(32), DataSize.ofMegabytes(256)));
        assertThatCode(() -> lenient.validate(pages)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejeita links que executam código")
    void rejectsJavascriptLinks() throws Exception {
        byte[] bytes = pdf(d -> d.getDocumentCatalog().getCOSObject().setString(COSName.URI, "javascript:alert(1)"));
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(bytes));
    }

    @Test
    @DisplayName("link malformado vira mensagem de domínio, sem vazar o erro do JDK")
    void rejectsMalformedLinkWithoutLeakingJdkMessage() throws Exception {
        byte[] bytes = pdf(d -> d.getDocumentCatalog().getCOSObject().setString(COSName.URI, "http://exemplo .com/cv"));
        assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> validator.validate(bytes))
                .withMessage("O PDF contém um link não permitido");
    }

    private static FileProperties properties(int maxPages, DataSize perPage, DataSize ceiling) {
        return new FileProperties(true, "bucket", "sa-east-1", null, false, 300, "localhost", 3310,
                maxPages, perPage, ceiling);
    }

    private static FileUpload upload(String name, String mime, byte[] bytes) {
        return new FileUpload(name, mime, bytes.length, () -> new ByteArrayInputStream(bytes));
    }
}
