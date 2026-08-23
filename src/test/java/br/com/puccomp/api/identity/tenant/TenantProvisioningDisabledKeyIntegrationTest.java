package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "puccomp.admin.key=")
class TenantProvisioningDisabledKeyIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("chave administrativa não configurada mantém /v1/admin fechado")
    void shouldRejectWhenPlatformKeyIsNotConfigured() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-puccomp-key", "test-admin-key");

        ResponseEntity<String> response = rest.exchange("/v1/admin/tenants", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "name", "EJ Sem Config",
                        "slug", "ej-sem-config",
                        "owner_email", "owner@sem-config.dev",
                        "courses", List.of("Computacao")), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
