package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.recruitment.processes.*;
import br.com.puccomp.api.shared.exception.ServiceUnavailableException;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import(TestSeeder.class)
@TestPropertySource(properties = {"puccomp.files.enabled=true", "puccomp.files.bucket=puccomp-private-dev"})
class CvUploadIntegrationTest extends AbstractIntegrationTest {
    @Autowired TestSeeder seeder;
    @Autowired JdbcClient jdbc;
    @Autowired FileService files;
    @Autowired PendingFileCleanup cleanup;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean ObjectStorage storage;
    @MockitoBean ClamAvScanner scanner;
    @MockitoBean br.com.puccomp.api.email.Mailer mailer;

    private UUID tenant;
    private UUID process;
    private String slug;
    private String token;

    @BeforeEach
    void prepare() {
        slug = "cv-" + UUID.randomUUID();
        tenant = seeder.seedTenant("EJ CV", slug);
        String email = slug + "@example.com";
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        token = login(email, "senha123");
        process = post("/v1/recruitment/processes", new SelectionProcessRequest("PS", null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + process + "/status", new ChangeStatusRequest(SelectionProcessStatus.OPEN),
                token, SelectionProcessResponse.class);
        when(storage.downloadUrl(anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn("https://private.example/cv?X-Amz-Signature=test");
    }

    @Test
    @DisplayName("upload público persiste CV privado e DTO autorizado inclui URL temporária")
    void uploadsAndDownloadsOnlyWithPermission() throws Exception {
        var response = submit("cv.pdf", "application/pdf", PdfValidatorTest.pdf(d -> { }));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContain("download_url", "cv.pdf", "X-Amz", "candidato@example.com");
        verify(scanner).scan(any());
        verify(storage).put(eq("puccomp-private-dev"), startsWith(tenant + "/files/"), any());
        var listing = getWithToken(internalPath(), token);
        assertThat(listing.getBody()).contains("\"cv\":{", "download_url", "download_expires_at", "cv.pdf", "X-Amz-Signature");
        assertThat(listing.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(getWithToken(internalPath(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        seeder.seedAccount(tenant, slug + "member@example.com", "senha123", Standing.MEMBER);
        assertThat(getWithToken(internalPath(), login(slug + "member@example.com", "senha123")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        UUID other = seeder.seedTenant("Outra EJ", "other-" + slug);
        seeder.seedAccount(other, slug + "other@example.com", "senha123", Standing.OWNER);
        assertThat(getWithToken(internalPath(), login(slug + "other@example.com", "senha123")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        UUID fileId = jdbc.sql("select cv_file_id from candidate_applications where process_id = ?")
                .param(process).query(UUID.class).single();
        TenantContext.set(other);
        try {
            assertThat(files.downloads(List.of(fileId))).isEmpty();
        } finally { TenantContext.clear(); }

        clearInvocations(storage, scanner);
        assertThat(submit("cv.pdf", "application/pdf", PdfValidatorTest.pdf(d -> { })).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(storage, scanner);
    }

    @Test
    @DisplayName("PDF malformado, MIME falso e scripts não chegam ao storage nem criam candidatura")
    void rejectsUnsafeFilesBeforeStorage() throws Exception {
        assertThat(submit("cv.pdf", "application/pdf", "%PDF-1.7 fake %%EOF".getBytes()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(submit("cv.pdf", "image/png", PdfValidatorTest.pdf(d -> { })).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        byte[] script = PdfValidatorTest.pdf(d -> d.getDocumentCatalog().getCOSObject()
                .setString(org.apache.pdfbox.cos.COSName.getPDFName("JavaScript"), "alert(1)"));
        assertThat(submit("cv.pdf", "application/pdf", script).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(storage);
        verifyNoInteractions(mailer);
        assertThat(applicationCount()).isZero();
    }

    @Test
    @DisplayName("falha do antivírus bloqueia o upload sem gravar arquivo")
    void failsClosedWhenScannerIsUnavailableOrDetectsMalware() throws Exception {
        doThrow(new ServiceUnavailableException("Scanner indisponível")).when(scanner).scan(any());
        assertThat(submit("cv.pdf", "application/pdf", PdfValidatorTest.pdf(d -> { })).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        doThrow(new br.com.puccomp.api.shared.exception.InvalidFileException("Arquivo infectado")).when(scanner).scan(any());
        assertThat(submit("cv.pdf", "application/pdf", PdfValidatorTest.pdf(d -> { })).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(storage);
        verifyNoInteractions(mailer);
        assertThat(applicationCount()).isZero();
        verifyNoInteractions(mailer);
    }

    @Test
    @DisplayName("falha do S3 reverte candidatura e deixa reserva para limpeza com retry")
    void cleansOrphansAfterStorageFailure() throws Exception {
        doThrow(new ServiceUnavailableException("timeout")).when(storage).put(anyString(), anyString(), any());
        assertThat(submit("cv.pdf", "application/pdf", PdfValidatorTest.pdf(d -> { })).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(applicationCount()).isZero();
        // O cliente HTTP pode repetir o POST após 503; todas as tentativas precisam ser recuperáveis.
        assertThat(jdbc.sql("select state from stored_files where tenant_id = ?").param(tenant).query(String.class).list())
                .isNotEmpty().containsOnly("PENDING");
        long pending = pendingCount();
        jdbc.sql("update stored_files set created_at = now() - interval '25 hours' where tenant_id = ?").param(tenant).update();
        doThrow(new ServiceUnavailableException("offline")).when(storage).delete(anyString(), anyString());
        cleanup.removeExpired();
        assertThat(pendingCount()).isEqualTo(pending);
        doNothing().when(storage).delete(anyString(), anyString());
        cleanup.removeExpired();
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("limite multipart rejeita arquivo grande com 413")
    void enforcesMultipartLimit() {
        assertThat(submit("cv.pdf", "application/pdf", new byte[PdfValidator.MAX_BYTES + 1]).getStatusCode())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        verifyNoInteractions(storage, scanner);
    }

    @Test
    @DisplayName("rollback do consumidor após PUT mantém arquivo pendente e indisponível para download")
    void rollsBackReadyStateWithConsumer() throws Exception {
        byte[] pdf = PdfValidatorTest.pdf(d -> { });
        TenantContext.set(tenant);
        try {
            UUID staged = files.stage(new br.com.puccomp.api.files.FileUpload("cv.pdf", "application/pdf", pdf.length,
                    () -> new java.io.ByteArrayInputStream(pdf)));
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                files.confirm(staged);
                status.setRollbackOnly();
            });
            verify(storage).put(anyString(), startsWith(tenant + "/files/"), eq(pdf));
            assertThat(jdbc.sql("select state from stored_files where id = ?").param(staged).query(String.class).single())
                    .isEqualTo("PENDING");
            assertThat(files.downloads(List.of(staged))).isEmpty();
        } finally { TenantContext.clear(); }
    }

    private long pendingCount() {
        return jdbc.sql("select count(*) from stored_files where tenant_id = ?").param(tenant).query(Long.class).single();
    }

    private long applicationCount() {
        return jdbc.sql("select count(*) from candidate_applications where tenant_id = ?").param(tenant).query(Long.class).single();
    }

    private String internalPath() { return "/v1/recruitment/processes/" + process + "/applications"; }

    private ResponseEntity<String> submit(String name, String contentType, byte[] bytes) {
        var body = new LinkedMultiValueMap<String, Object>();
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("application", new HttpEntity<>(Map.of("full_name", "Ana", "email", "candidato@example.com",
                "phone", "31999990000", "course", "Computação", "privacy_consent", true), jsonHeaders));
        var fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("cv", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override public String getFilename() { return name; }
        }, fileHeaders));
        return post("/v1/public/" + slug + "/processes/" + process + "/applications", body, null, String.class);
    }
}
