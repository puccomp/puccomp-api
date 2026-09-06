package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Testcontainers
@Import(TestSeeder.class)
@TestPropertySource(properties = {"puccomp.files.enabled=true", "puccomp.files.bucket=puccomp-private-dev"})
class CvSubmissionEndToEndTest extends AbstractIntegrationTest {
    // O ciclo de vida é do @Testcontainers, que para o container depois da classe; o analisador
    // não enxerga isso na cadeia fluente e acusa vazamento.
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", "testaccess")
            .withEnv("MINIO_ROOT_PASSWORD", "testsecret123")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @Autowired TestSeeder seeder;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean ClamAvScanner scanner;
    @TestBean(name = "mailTaskExecutor") TaskExecutor mailTaskExecutor;
    @TestBean(name = "objectStorage") ObjectStorage storage;

    static TaskExecutor mailTaskExecutor() { return new SyncTaskExecutor(); }

    static ObjectStorage objectStorage() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("testaccess", "testsecret123"));
        var endpoint = URI.create("http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        var configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var client = S3Client.builder().endpointOverride(endpoint).region(Region.SA_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(configuration).build();
        client.createBucket(r -> r.bucket("puccomp-private-dev"));
        var signer = S3Presigner.builder().endpointOverride(endpoint).region(Region.SA_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(configuration).build();
        return new S3ObjectStorage(client, signer);
    }

    @Test
    @DisplayName("candidatura HTTP salva PDF no S3 e envia emails apenas ao candidato, dono e recrutador")
    @SuppressWarnings("unchecked")
    void submitsStoresDownloadsAndNotifiesCorrectRecipients() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        String slug = "cv-e2e-" + UUID.randomUUID();
        UUID tenant = seeder.seedTenant("EJ Integração", slug);
        UUID courseId = seeder.seedCourse(tenant, "Computação");
        seeder.seedAccount(tenant, "owner@cv-e2e.dev", "senha123", Standing.OWNER);
        String token = login("owner@cv-e2e.dev", "senha123");
        UUID role = seeder.seedCargo(tenant, "Recrutamento");
        put("/v1/roles/" + role + "/permissions", Map.of("permissions", List.of("recruitment:read")), token, String.class);
        seeder.seedAccount(tenant, "recruiter@cv-e2e.dev", "senha123", Standing.MEMBER, role);
        seeder.seedAccount(tenant, "unrelated@cv-e2e.dev", "senha123", Standing.MEMBER);
        UUID process = post("/v1/recruitment/processes", new SelectionProcessRequest("PS Currículos", null, null, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + process + "/status", new ChangeStatusRequest(SelectionProcessStatus.OPEN),
                token, SelectionProcessResponse.class);

        byte[] pdf = PdfValidatorTest.pdf(d -> { });
        var body = new LinkedMultiValueMap<String, Object>();
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("application", new HttpEntity<>(Map.of("full_name", "Ana Lima", "email", "candidate@cv-e2e.dev",
                "phone", "31999990000", "course_id", courseId.toString(), "privacy_consent", true), jsonHeaders));
        var fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        body.add("cv", new HttpEntity<>(new ByteArrayResource(pdf) {
            @Override public String getFilename() { return "Currículo Ana.pdf"; }
        }, fileHeaders));
        var submission = post("/v1/public/" + slug + "/processes/" + process + "/applications", body, null, String.class);
        assertThat(submission.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submission.getBody()).doesNotContain("download_url", "candidate@cv-e2e.dev");
        verify(scanner).scan(pdf);

        var messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(messages.capture());
        var recipients = new java.util.HashSet<String>();
        for (MimeMessage message : messages.getAllValues()) {
            assertThat(message.getAllRecipients()).hasSize(1);
            String recipient = message.getAllRecipients()[0].toString();
            recipients.add(recipient);
            assertThat(message.getSubject()).isEqualTo(recipient.equals("candidate@cv-e2e.dev")
                    ? "Inscrição confirmada — PS Currículos" : "Nova inscrição — PS Currículos");
        }
        assertThat(recipients).containsExactlyInAnyOrder("candidate@cv-e2e.dev", "owner@cv-e2e.dev", "recruiter@cv-e2e.dev");

        var listing = get("/v1/recruitment/processes/" + process + "/applications", token,
                new ParameterizedTypeReference<Map<String, Object>>() { }).getBody();
        var application = ((List<Map<String, Object>>) listing.get("content")).getFirst();
        var cv = (Map<String, Object>) application.get("cv");
        URI download = URI.create((String) cv.get("download_url"));
        assertThat(download.getPath()).contains(tenant + "/files/");
        assertThat(download.getQuery()).contains("X-Amz-Signature=", "X-Amz-Expires=300");
        assertThat(cv.get("filename")).isEqualTo("Currículo Ana.pdf");
        try (var http = HttpClient.newHttpClient()) {
            var downloaded = http.send(HttpRequest.newBuilder(download).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(downloaded.statusCode()).isEqualTo(200);
            assertThat(downloaded.body()).isEqualTo(pdf);
            assertThat(downloaded.headers().firstValue("Content-Disposition")).hasValueSatisfying(v -> assertThat(v).startsWith("attachment;"));
            assertThat(downloaded.headers().firstValue("Content-Type")).contains("application/octet-stream");
            URI unsigned = new URI(download.getScheme(), download.getAuthority(), download.getPath(), null, null);
            assertThat(http.send(HttpRequest.newBuilder(unsigned).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isEqualTo(403);
            URI tampered = URI.create(download.toString().replace("X-Amz-Expires=300", "X-Amz-Expires=900"));
            assertThat(http.send(HttpRequest.newBuilder(tampered).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isEqualTo(403);
        }
    }

}
