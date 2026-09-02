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
            "/v1/financial/entries", "/v1/recruitment/processes/{processId}/candidacies"})
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
