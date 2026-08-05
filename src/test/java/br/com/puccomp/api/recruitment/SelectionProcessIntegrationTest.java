package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    @DisplayName("deve criar e listar processos seletivos para a EJ logada")
    void shouldCreateAndListSelectionProcesses() {
        UUID tenantId = seeder.seedTenant("EJ Recrutamento", "ej-recrutamento");
        seeder.seedAccount(tenantId, "dono@recrutamento.dev", "senha123", Standing.OWNER);

        String token = login("dono@recrutamento.dev", "senha123");

        SelectionProcessRequest request = new SelectionProcessRequest(
                "Processo Seletivo 2026.1",
                "Descrição do processo 2026.1",
                Instant.now(),
                Instant.now().plusSeconds(86400)
        );

        ResponseEntity<SelectionProcessResponse> createResponse = post("/v1/recruitment/processes", request, token, SelectionProcessResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().title()).isEqualTo("Processo Seletivo 2026.1");
        assertThat(createResponse.getBody().status()).isEqualTo(SelectionProcessStatus.DRAFT);

        UUID processId = createResponse.getBody().id();

        ResponseEntity<SelectionProcessResponse> getResponse = getWithToken("/v1/recruitment/processes/" + processId, token, SelectionProcessResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id()).isEqualTo(processId);

        ResponseEntity<List<SelectionProcessResponse>> listResponse = getListWithToken("/v1/recruitment/processes", token);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("deve isolar processos seletivos entre empresas juniores diferentes")
    void shouldIsolateSelectionProcessesBetweenTenants() {
        UUID tenantA = seeder.seedTenant("EJ Alpha", "ej-alpha");
        seeder.seedAccount(tenantA, "dono@alpha.dev", "senha123", Standing.OWNER);
        String tokenA = login("dono@alpha.dev", "senha123");

        UUID tenantB = seeder.seedTenant("EJ Beta", "ej-beta");
        seeder.seedAccount(tenantB, "dono@beta.dev", "senha123", Standing.OWNER);
        String tokenB = login("dono@beta.dev", "senha123");

        SelectionProcessRequest requestA = new SelectionProcessRequest("Processo Alpha", "Edital A", null, null);
        post("/v1/recruitment/processes", requestA, tokenA, SelectionProcessResponse.class);

        ResponseEntity<List<SelectionProcessResponse>> listB = getListWithToken("/v1/recruitment/processes", tokenB);
        assertThat(listB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listB.getBody()).isEmpty();
    }

    private <T> ResponseEntity<T> post(String path, Object body, String bearerToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private <T> ResponseEntity<T> getWithToken(String path, String bearerToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<List<SelectionProcessResponse>> getListWithToken(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<SelectionProcessResponse>>() {});
    }
}
