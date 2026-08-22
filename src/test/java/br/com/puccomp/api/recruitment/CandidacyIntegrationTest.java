package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.candidacies.CandidacyReceiptResponse;
import br.com.puccomp.api.recruitment.candidacies.CandidacyStatus;
import br.com.puccomp.api.recruitment.candidacies.SubmitCandidacyRequest;
import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.PublicProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class CandidacyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("candidato anônimo deve ver o processo aberto e se inscrever pelo slug da EJ")
    void shouldBrowseAndSubmitThroughPublicSurface() {
        String token = ownerOf("EJ Alpha", "ej-alpha-cand", "dono@alpha-cand.dev");
        UUID processId = openProcess(token, "Processo Alpha", "Edital A");

        ResponseEntity<List<PublicProcessResponse>> open = get("/v1/public/ej-alpha-cand/processes", null,
                new ParameterizedTypeReference<List<PublicProcessResponse>>() { });
        assertThat(open.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(open.getBody()).singleElement()
                .satisfies(p -> assertThat(p.title()).isEqualTo("Processo Alpha"));

        ResponseEntity<PublicProcessResponse> detail =
                get(publicProcess("ej-alpha-cand", processId), null, PublicProcessResponse.class);
        assertThat(detail.getBody().description()).isEqualTo("Edital A");

        ResponseEntity<CandidacyReceiptResponse> submit = post(publicProcess("ej-alpha-cand", processId)
                + "/candidacies", candidacy("joao@example.com"), null, CandidacyReceiptResponse.class);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submit.getBody().status()).isEqualTo(CandidacyStatus.SUBMITTED);

        assertThat(getWithToken("/v1/recruitment/processes/" + processId + "/candidacies", token).getBody())
                .contains("joao@example.com");
    }

    @Test
    @DisplayName("processo em DRAFT deve ser indistinguível de inexistente na superfície pública")
    void shouldHideDraftProcessesFromPublicSurface() {
        String token = ownerOf("EJ Rascunho", "ej-rascunho", "dono@rascunho.dev");
        UUID processId = createProcess(token, "Processo ainda não publicado");

        assertThat(get("/v1/public/ej-rascunho/processes", null,
                new ParameterizedTypeReference<List<PublicProcessResponse>>() { }).getBody()).isEmpty();

        assertThat(get(publicProcess("ej-rascunho", processId), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(post(publicProcess("ej-rascunho", processId) + "/candidacies",
                candidacy("cedo@example.com"), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("slug de EJ inexistente deve devolver 404 sem vazar nada")
    void shouldRejectUnknownSlug() {
        ResponseEntity<ErrorResponse> response =
                get("/v1/public/ej-que-nao-existe/processes", null, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Empresa júnior não encontrada");
    }

    @Test
    @DisplayName("o slug da URL deve mandar sobre o tenant de quem está autenticado")
    void shouldBindCandidacyToSlugTenantNotToCallerTenant() {
        String tokenA = ownerOf("EJ Alpha", "ej-alpha-bind", "dono@alpha-bind.dev");
        String tokenB = ownerOf("EJ Beta", "ej-beta-bind", "dono@beta-bind.dev");
        UUID processA = openProcess(tokenA, "Processo Alpha", null);

        // Membro autenticado da EJ B usando a superfície pública da EJ A.
        assertThat(post(publicProcess("ej-alpha-bind", processA) + "/candidacies",
                candidacy("espiao@example.com"), tokenB, CandidacyReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(getWithToken("/v1/recruitment/processes/" + processA + "/candidacies", tokenA).getBody())
                .contains("espiao@example.com");
        assertThat(getWithToken("/v1/recruitment/processes/" + processA + "/candidacies", tokenB)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("não deve aceitar inscrição duplicada para o mesmo email, ignorando maiúsculas")
    void shouldRejectDuplicateCandidacyIgnoringCase() {
        String token = ownerOf("EJ Duplicada", "ej-duplicada", "dono@duplicada.dev");
        String path = publicProcess("ej-duplicada", openProcess(token, "Processo", null)) + "/candidacies";

        assertThat(post(path, candidacy("maria@example.com"), null, CandidacyReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorResponse> duplicate =
                post(path, candidacy("MARIA@example.com"), null, ErrorResponse.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().message()).contains("Você já se inscreveu");
    }

    @Test
    @DisplayName("não deve aceitar inscrição em processo que saiu de OPEN")
    void shouldRejectCandidacyWhenProcessLeavesOpen() {
        String token = ownerOf("EJ Fechada", "ej-fechada", "dono@fechada.dev");
        UUID processId = openProcess(token, "Processo", null);

        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.CLOSED), token, SelectionProcessResponse.class);

        ResponseEntity<ErrorResponse> submit = post(publicProcess("ej-fechada", processId) + "/candidacies",
                candidacy("tarde@example.com"), null, ErrorResponse.class);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(submit.getBody().message()).contains("não está aceitando inscrições");
    }

    @Test
    @DisplayName("não deve aceitar inscrição depois do prazo, mesmo com o processo OPEN")
    void shouldRejectCandidacyAfterDeadline() {
        String token = ownerOf("EJ Prazo", "ej-prazo", "dono@prazo.dev");

        Instant past = Instant.now().minusSeconds(7_200);
        UUID processId = post("/v1/recruitment/processes",
                new SelectionProcessRequest("Processo", null, past, past.plusSeconds(3_600)),
                token, SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);

        ResponseEntity<ErrorResponse> submit = post(publicProcess("ej-prazo", processId) + "/candidacies",
                candidacy("atrasado@example.com"), null, ErrorResponse.class);

        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(submit.getBody().message()).contains("fora do prazo");
    }

    private String ownerOf(String ejName, String slug, String email) {
        UUID tenantId = seeder.seedTenant(ejName, slug);
        seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private UUID createProcess(String token, String title) {
        return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null, null, null),
                token, SelectionProcessResponse.class).getBody().id();
    }

    private UUID openProcess(String token, String title, String description) {
        UUID processId = post("/v1/recruitment/processes",
                new SelectionProcessRequest(title, description, null, null), token,
                SelectionProcessResponse.class).getBody().id();

        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);
        return processId;
    }

    private static String publicProcess(String ejSlug, UUID processId) {
        return "/v1/public/" + ejSlug + "/processes/" + processId;
    }

    private static SubmitCandidacyRequest candidacy(String email) {
        return new SubmitCandidacyRequest("João Silva", email, "+55 31 99999-8888",
                "Sistemas de Informação", "3º", null, null, true);
    }
}
