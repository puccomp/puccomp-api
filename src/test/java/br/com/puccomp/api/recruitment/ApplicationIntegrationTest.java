package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.applications.ApplicationResponse;
import br.com.puccomp.api.recruitment.applications.ApplicationStatus;
import br.com.puccomp.api.recruitment.applications.CreateApplicationRequest;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("deve enviar candidatura e listar corretamente com isolamento de tenant")
    void shouldSubmitAndListApplicationsWithTenantIsolation() {
        UUID tenantA = seeder.seedTenant("EJ Alpha", "ej-alpha-app");
        seeder.seedAccount(tenantA, "dono@alpha-app.dev", "senha123", Standing.OWNER);
        String tokenA = login("dono@alpha-app.dev", "senha123");

        UUID tenantB = seeder.seedTenant("EJ Beta", "ej-beta-app");
        seeder.seedAccount(tenantB, "dono@beta-app.dev", "senha123", Standing.OWNER);
        String tokenB = login("dono@beta-app.dev", "senha123");

        SelectionProcessRequest requestA = new SelectionProcessRequest("Processo Alpha", "Edital A", null, null);
        ResponseEntity<SelectionProcessResponse> createProcessResponse = post("/v1/recruitment/processes", requestA, tokenA, SelectionProcessResponse.class);
        UUID processId = createProcessResponse.getBody().id();

        patch("/v1/recruitment/processes/" + processId + "/status?status=OPEN", null, tokenA, Void.class);

        CreateApplicationRequest applicationRequest = new CreateApplicationRequest(
                "João Silva",
                "joao@example.com",
                "+55 31 99999-8888",
                "PUC Minas",
                "Sistemas de Informação",
                "3º",
                null,
                null,
                true
        );

        ResponseEntity<ApplicationResponse> submitResponse = post("/v1/recruitment/processes/" + processId + "/applications", applicationRequest, null, ApplicationResponse.class);
        
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitResponse.getBody().fullName()).isEqualTo("João Silva");
        assertThat(submitResponse.getBody().status()).isEqualTo(ApplicationStatus.SUBMITTED);

        ResponseEntity<String> listB = getWithToken("/v1/recruitment/processes/" + processId + "/applications", tokenB, String.class);
        assertThat(listB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> listA = getWithToken("/v1/recruitment/processes/" + processId + "/applications", tokenA, String.class);
        assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listA.getBody()).contains("joao@example.com");
    }

    @Test
    @DisplayName("nao deve permitir candidatura duplicada para o mesmo email no mesmo processo")
    void shouldNotAllowDuplicateApplications() {
        UUID tenantId = seeder.seedTenant("EJ Duplicada", "ej-duplicada");
        seeder.seedAccount(tenantId, "dono@duplicada.dev", "senha123", Standing.OWNER);
        String token = login("dono@duplicada.dev", "senha123");

        SelectionProcessRequest request = new SelectionProcessRequest("Processo", "Edital", null, null);
        ResponseEntity<SelectionProcessResponse> createProcessResponse = post("/v1/recruitment/processes", request, token, SelectionProcessResponse.class);
        UUID processId = createProcessResponse.getBody().id();

        patch("/v1/recruitment/processes/" + processId + "/status?status=OPEN", null, token, Void.class);

        CreateApplicationRequest applicationRequest = new CreateApplicationRequest(
                "Maria Souza",
                "maria@example.com",
                "+55 31 99999-7777",
                "PUC Minas",
                "Ciência da Computação",
                "5º",
                null,
                null,
                true
        );

        ResponseEntity<ApplicationResponse> firstSubmit = post("/v1/recruitment/processes/" + processId + "/applications", applicationRequest, null, ApplicationResponse.class);
        assertThat(firstSubmit.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorResponse> secondSubmit = post("/v1/recruitment/processes/" + processId + "/applications", applicationRequest, null, ErrorResponse.class);
        assertThat(secondSubmit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondSubmit.getBody().message()).contains("Você já enviou uma candidatura");
    }

    private <T> ResponseEntity<T> post(String path, Object body, String bearerToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private <T> ResponseEntity<T> patch(String path, Object body, String bearerToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), responseType);
    }

    private <T> ResponseEntity<T> getWithToken(String path, String bearerToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }
}
