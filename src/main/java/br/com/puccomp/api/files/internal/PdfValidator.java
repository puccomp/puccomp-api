package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.files.FileUpload;
import br.com.puccomp.api.shared.exception.InvalidFileException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

@Component
class PdfValidator {
    /** Fixo de propósito: casa com spring.servlet.multipart.max-file-size e com o CHECK da V12. */
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> FORBIDDEN_KEYS = Set.of("JS", "JavaScript", "AA",
            "EmbeddedFiles", "EF", "AF", "XFA", "RichMediaContent", "RichMediaSettings",
            "Collection", "Movie", "Sound", "3DD", "3DA");
    private static final Set<String> FORBIDDEN_ACTIONS = Set.of("JavaScript", "Launch", "GoToR", "GoToE",
            "SubmitForm", "ImportData", "Rendition", "Movie", "Sound");

    private final FileProperties properties;

    PdfValidator(FileProperties properties) { this.properties = properties; }

    String filename(FileUpload upload) {
        if (upload == null || upload.content() == null || upload.filename() == null
                || !upload.filename().matches("[\\p{L}\\p{N}][\\p{L}\\p{N} _().-]{0,115}\\.[pP][dD][fF]")
                || upload.filename().contains(".."))
            throw new InvalidFileException("Envie um PDF com nome simples de até 120 caracteres, sem caminhos");
        if (!"application/pdf".equalsIgnoreCase(upload.contentType()))
            throw new InvalidFileException("O tipo do currículo deve ser application/pdf");
        if (upload.size() <= 0 || upload.size() > MAX_BYTES)
            throw new InvalidFileException("O currículo deve ter entre 1 byte e 5 MiB");
        return upload.filename();
    }

    byte[] read(FileUpload upload) {
        try (var input = upload.content().open()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length != upload.size() || bytes.length > MAX_BYTES)
                throw new InvalidFileException("Tamanho do currículo inválido; o limite é 5 MiB");
            if (bytes.length < 12 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                    || !new String(bytes, Math.max(0, bytes.length - 32), Math.min(32, bytes.length),
                    StandardCharsets.US_ASCII).stripTrailing().endsWith("%%EOF"))
                throw invalid();
            return bytes;
        } catch (IOException e) {
            throw new InvalidFileException("Não foi possível ler o currículo");
        }
    }

    void validate(byte[] bytes) {
        try (var source = new RandomAccessReadBuffer(bytes)) {
            var parser = new PDFParser(source);
            try (var pdf = parser.parse(false)) {
                int pages = pdf.getNumberOfPages();
                if (pdf.isEncrypted() || pages < 1 || pages > properties.maxPages())
                    throw new InvalidFileException("O PDF deve ter de 1 a " + properties.maxPages()
                            + " páginas e não pode ser criptografado");
                inspect(pdf.getDocument().getTrailer(), properties.decompressionBudget(pages));
            }
        } catch (IOException e) {
            throw invalid();
        }
    }

    private void inspect(COSBase root, long decompressionBudget) throws IOException {
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<COSBase>();
        pending.add(root);
        long decodedBytes = 0;
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        byte[] buffer = new byte[8192];
        while (!pending.isEmpty()) {
            if (visited.size() + pending.size() > 20000 || System.nanoTime() > deadline)
                throw new InvalidFileException("PDF complexo demais para um currículo");
            COSBase value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (value instanceof COSObject object) {
                if (object.getObject() != null) pending.add(object.getObject());
            } else if (value instanceof COSDictionary dictionary) {
                for (COSName key : dictionary.keySet()) {
                    if (FORBIDDEN_KEYS.contains(key.getName()))
                        throw new InvalidFileException("O PDF não pode conter scripts, formulários ou anexos");
                    COSBase child = dictionary.getItem(key);
                    if (child != null) pending.add(child);
                }
                if (FORBIDDEN_ACTIONS.contains(dictionary.getNameAsString(COSName.S, ""))) throw invalid();
                if (dictionary.containsKey(COSName.URI)) checkLink(dictionary.getString(COSName.URI));
                if (value instanceof COSStream stream) {
                    // Impede que streams comprimidos pequenos escondam conteúdo desproporcional.
                    try (var decoded = stream.createInputStream()) {
                        int read;
                        while ((read = decoded.read(buffer)) != -1) {
                            decodedBytes += read;
                            if (decodedBytes > decompressionBudget || System.nanoTime() > deadline)
                                throw new InvalidFileException("Conteúdo descomprimido do PDF excede o limite; "
                                        + "reduza a resolução das imagens ou o número de páginas");
                        }
                    }
                }
            } else if (value instanceof COSArray array) {
                for (COSBase item : array) if (item != null) pending.add(item);
            }
        }
    }

    private static void checkLink(String link) {
        String scheme = null;
        // URI.create rejeita link não codificado com a mensagem do JDK; ela não pode vazar na resposta.
        if (link != null) try { scheme = URI.create(link).getScheme(); } catch (IllegalArgumentException ignored) { }
        if (scheme == null || !Set.of("https", "http", "mailto").contains(scheme.toLowerCase(Locale.ROOT)))
            throw new InvalidFileException("O PDF contém um link não permitido");
    }

    private static InvalidFileException invalid() {
        return new InvalidFileException("O arquivo não é um PDF válido e permitido");
    }
}
