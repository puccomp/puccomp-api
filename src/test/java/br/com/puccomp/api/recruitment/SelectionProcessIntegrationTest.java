package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
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
class SelectionProcessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("deve criar processo em DRAFT e devolvê-lo na listagem da EJ")
    void shouldCreateAndListSelectionProcesses() {
        String token = ownerOf("EJ Recrutamento", "ej-recrutamento", "dono@recrutamento.dev");

        var request = new SelectionProcessRequest(
                "Processo Seletivo 2026.1",
                "Descrição do processo 2026.1",
                Instant.now(),
                Instant.now().plusSeconds(86_400));

        ResponseEntity<SelectionProcessResponse> created =
                post("/v1/recruitment/processes", request, token, SelectionProcessResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().title()).isEqualTo("Processo Seletivo 2026.1");
        assertThat(created.getBody().status()).isEqualTo(SelectionProcessStatus.DRAFT);

        ResponseEntity<List<SelectionProcessResponse>> list = get("/v1/recruitment/processes", token,
                new ParameterizedTypeReference<List<SelectionProcessResponse>>() { });
        assertThat(list.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("deve isolar processos seletivos entre empresas juniores diferentes")
    void shouldIsolateSelectionProcessesBetweenTenants() {
        String tokenA = ownerOf("EJ Alpha", "ej-alpha", "dono@alpha.dev");
        String tokenB = ownerOf("EJ Beta", "ej-beta", "dono@beta.dev");

        UUID processId = createProcess(tokenA, "Processo Alpha");

        ResponseEntity<List<SelectionProcessResponse>> listB = get("/v1/recruitment/processes", tokenB,
                new ParameterizedTypeReference<List<SelectionProcessResponse>>() { });
        assertThat(listB.getBody()).isEmpty();

        ResponseEntity<String> readB = getWithToken("/v1/recruitment/processes/" + processId, tokenB);
        assertThat(readB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deve recusar transição de status inválida")
    void shouldRejectInvalidStatusTransition() {
        String token = ownerOf("EJ Transicao", "ej-transicao", "dono@transicao.dev");
        UUID processId = createProcess(token, "Processo");

        ResponseEntity<ErrorResponse> jump = patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.FINISHED), token, ErrorResponse.class);
        assertThat(jump.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jump.getBody().message()).contains("Não é possível mudar o processo de DRAFT para FINISHED");

        assertThat(patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.CANCELLED), token, SelectionProcessResponse.class)
                .getBody().status()).isEqualTo(SelectionProcessStatus.CANCELLED);

        ResponseEntity<ErrorResponse> reopen = patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, ErrorResponse.class);
        assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("deve recusar processo cuja data de término precede a de início")
    void shouldRejectInvertedPeriod() {
        String token = ownerOf("EJ Datas", "ej-datas", "dono@datas.dev");

        Instant start = Instant.now();
        ResponseEntity<ErrorResponse> response = post("/v1/recruitment/processes",
                new SelectionProcessRequest("Processo", null, start, start.minusSeconds(3_600)),
                token, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("data de término deve ser posterior");
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
}
