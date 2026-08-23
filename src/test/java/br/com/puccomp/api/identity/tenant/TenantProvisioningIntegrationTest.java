package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantRepository tenants;

    @Test
    @DisplayName("provisiona EJ, aceita convite OWNER, loga e cria cargo")
    void shouldProvisionTenantAndCompleteOwnerFlow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map<String, Object>> provisioned = provision(
                "EJ Admin " + suffix,
                "EJ Admin " + suffix,
                "owner-" + suffix + "@admin.dev",
                "test-admin-key");

        assertThat(provisioned.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(provisioned.getBody().get("slug")).isEqualTo("ej-admin-" + suffix);
        @SuppressWarnings("unchecked")
        Map<String, Object> invitation = (Map<String, Object>) provisioned.getBody().get("invitation");
        String acceptUrl = (String) invitation.get("accept_url");
        assertThat(acceptUrl).contains("token=inv_");

        String token = acceptUrl.substring(acceptUrl.indexOf("token=") + "token=".length());
        UUID courseId = firstCourseId(token);

        ResponseEntity<Map<String, Object>> accepted = rest.exchange("/v1/invitations/accept", HttpMethod.POST,
                json(Map.of(
                        "token", token,
                        "password", "senha123",
                        "name", "Owner Admin",
                        "course_id", courseId)),
                map());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);

        String ownerToken = login("owner-" + suffix + "@admin.dev", "senha123");
        ResponseEntity<Map<String, Object>> me = rest.exchange("/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), map());
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> member = (Map<String, Object>) me.getBody().get("member");
        assertThat(member.get("standing")).isEqualTo("OWNER");

        ResponseEntity<Map<String, Object>> role = rest.exchange("/v1/roles", HttpMethod.POST,
                jsonWithBearer(Map.of(
                        "name", "Diretoria " + suffix,
                        "description", "Diretoria criada no onboarding",
                        "hierarchy_level", 1), ownerToken),
                map());
        assertThat(role.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("sem chave ou com chave errada retorna 401")
    void shouldRequirePlatformKey() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        ResponseEntity<String> missing = rest.exchange("/v1/admin/tenants", HttpMethod.POST,
                jsonString(body("EJ Sem Chave " + suffix, "ej-sem-chave-" + suffix,
                        "missing-" + suffix + "@admin.dev")),
                String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-puccomp-key", "wrong-key");
        ResponseEntity<String> wrong = rest.exchange("/v1/admin/tenants", HttpMethod.POST,
                new HttpEntity<>(body("EJ Chave Errada " + suffix, "ej-chave-errada-" + suffix,
                        "wrong-" + suffix + "@admin.dev"), headers),
                String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("slug duplicado retorna 409")
    void shouldRejectDuplicateSlug() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "EJ Duplicada " + suffix;

        assertThat(provision("EJ Duplicada " + suffix, slug, "first-" + suffix + "@admin.dev",
                "test-admin-key").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long tenantCount = tenants.count();

        ResponseEntity<Map<String, Object>> duplicate = provision(
                "EJ Duplicada Outra " + suffix,
                slug,
                "second-" + suffix + "@admin.dev",
                "test-admin-key");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tenants.count()).isEqualTo(tenantCount);
    }

    private ResponseEntity<Map<String, Object>> provision(String name, String slug, String ownerEmail, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-puccomp-key", key);
        return rest.exchange("/v1/admin/tenants", HttpMethod.POST,
                new HttpEntity<>(body(name, slug, ownerEmail), headers), map());
    }

    private UUID firstCourseId(String token) {
        ResponseEntity<Map<String, Object>> preview = rest.exchange(
                "/v1/invitations/accept?token=" + token, HttpMethod.GET, HttpEntity.EMPTY, map());
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) preview.getBody().get("courses");
        return UUID.fromString((String) courses.getFirst().get("id"));
    }

    private Map<String, Object> body(String name, String slug, String ownerEmail) {
        return Map.of(
                "name", name,
                "slug", slug,
                "owner_email", ownerEmail,
                "courses", List.of("Computacao", "Administracao"));
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Map<String, Object>> jsonWithBearer(Map<String, Object> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Map<String, Object>> jsonString(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ParameterizedTypeReference<Map<String, Object>> map() {
        return new ParameterizedTypeReference<>() { };
    }
}
