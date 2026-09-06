package br.com.puccomp.api.config;

import br.com.puccomp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

/**
 * O spec publicado é o contrato. Sem estes testes ele já divergiu do servidor uma vez: o
 * {@code Pageable} saía como um objeto {@code {page, size, sort}}, enquanto o Spring espera
 * {@code ?page=&size=&sort=} achatado — todo cliente gerado a partir dele montava query inválida.
 */
class OpenApiContractTest extends AbstractIntegrationTest {

    @ParameterizedTest(name = "{0} publica page, size e sort achatados")
    @ValueSource(strings = {"/v1/members", "/v1/roles", "/v1/departments", "/v1/invitations",
            "/v1/financial/entries", "/v1/recruitment/processes/{processId}/applications"})
    void shouldFlattenPageableIntoQueryParams(String path) {
        assertThat(parameterNames(path)).contains("page", "size", "sort").doesNotContain("pageable");
    }

    @Test
    @DisplayName("declara os dois modos de credencial e exige bearer por padrão")
    void shouldDeclareSecuritySchemes() {
        Map<String, Object> spec = spec();

        assertThat(spec.get("components")).asInstanceOf(map(String.class, Object.class))
                .extractingByKey("securitySchemes").asInstanceOf(map(String.class, Object.class))
                .containsKeys("bearerAuth", "platformKey");
        assertThat(spec.get("security")).asInstanceOf(list(Object.class))
                .singleElement().asInstanceOf(map(String.class, Object.class)).containsKey("bearerAuth");
    }

    @Test
    @DisplayName("rota anônima não pede credencial; provisionamento pede a chave de plataforma")
    void shouldOverrideSecurityWhereItDiffers() {
        assertThat(operationSecurity("/v1/auth/login", "post")).isEmpty();
        assertThat(operationSecurity("/v1/public/{orgSlug}/processes", "get")).isEmpty();
        assertThat(operationSecurity("/v1/admin/organizations", "post"))
                .singleElement().asInstanceOf(map(String.class, Object.class)).containsKey("platformKey");
    }

    @Test
    @DisplayName("recrutamento publica inscrições sem expor cadastro isolado de candidato")
    @SuppressWarnings("unchecked")
    void shouldExposeOnlyCandidateApplicationSurface() {
        var paths = (Map<String, Object>) spec().get("paths");

        assertThat(paths)
                .containsKeys(
                        "/v1/recruitment/processes/{processId}/applications",
                        "/v1/public/{orgSlug}/processes/{processId}/applications")
                .doesNotContainKeys(
                        "/v1/recruitment/candidates",
                        "/v1/recruitment/candidates/{id}",
                        "/v1/recruitment/processes/{processId}/candidacies",
                        "/v1/public/{orgSlug}/processes/{processId}/candidacies");
    }

    @Test
    @DisplayName("inscrição aceita JSON e multipart; URL de currículo só aparece no DTO privado")
    @SuppressWarnings("unchecked")
    void shouldDescribeCvUploadAndPrivateDownload() {
        var post = operation("/v1/public/{orgSlug}/processes/{processId}/applications", "post");
        var requestBody = (Map<String, Object>) post.get("requestBody");
        assertThat((Map<String, Object>) requestBody.get("content"))
                .containsKeys("application/json", "multipart/form-data");
        var components = (Map<String, Object>) spec().get("components");
        var schemas = (Map<String, Object>) components.get("schemas");
        var response = (Map<String, Object>) schemas.get("CandidateApplicationResponse");
        assertThat((Map<String, Object>) response.get("properties")).containsKey("cv");
        var receipt = (Map<String, Object>) schemas.get("CandidateApplicationReceiptResponse");
        assertThat((Map<String, Object>) receipt.get("properties")).containsOnlyKeys("id", "submitted_at");
        var download = (Map<String, Object>) schemas.get("FileDownload");
        assertThat((Map<String, Object>) download.get("properties"))
                .containsOnlyKeys("id", "filename", "content_type", "size", "download_url", "download_expires_at");
    }

    @Test
    @DisplayName("respostas saem como application/json, não como */*")
    void shouldDeclareJsonResponses() {
        assertThat(operation("/v1/auth/me", "get").get("responses"))
                .asInstanceOf(map(String.class, Object.class))
                .extractingByKey("200").asInstanceOf(map(String.class, Object.class))
                .extractingByKey("content").asInstanceOf(map(String.class, Object.class))
                .containsOnlyKeys("application/json");
    }

    @SuppressWarnings("unchecked")
    private List<String> parameterNames(String path) {
        var parameters = (List<Map<String, Object>>) operation(path, "get").get("parameters");
        return parameters.stream().map(parameter -> (String) parameter.get("name")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> operationSecurity(String path, String method) {
        var security = (List<Object>) operation(path, method).get("security");
        return security == null ? List.of() : security;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(String path, String method) {
        var paths = (Map<String, Object>) spec().get("paths");
        assertThat(paths).containsKey(path);
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }

    private Map<String, Object> spec() {
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v3/api-docs", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }
}
