package br.com.puccomp.api.identity.security;

import br.com.puccomp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsIntegrationTest extends AbstractIntegrationTest {

    private static final String FRONT = "https://puccomp.com.br";

    @Test
    @DisplayName("preflight vindo do front configurado deve ser liberado com os cabeçalhos de CORS")
    void shouldAllowConfiguredOrigin() {
        ResponseEntity<String> response = preflight("/v1/auth/login", FRONT, List.of("authorization", "content-type"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(FRONT);
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.POST);
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("preflight de origem não configurada deve ser recusado")
    void shouldRejectUnknownOrigin() {
        ResponseEntity<String> response = preflight("/v1/auth/login", "https://site-aleatorio.com",
                List.of("authorization", "content-type"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    @DisplayName("a superfície administrativa também responde ao preflight, com o header da chave")
    void shouldAllowAdminPreflight() {
        ResponseEntity<String> response = preflight("/v1/admin/organizations", FRONT,
                List.of("x-puccomp-key", "content-type"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(FRONT);
        assertThat(response.getHeaders().getAccessControlAllowHeaders()).contains("x-puccomp-key");
    }

    @Test
    @DisplayName("o endpoint MCP responde ao preflight com os cabeçalhos do protocolo")
    void shouldAllowMcpPreflight() {
        ResponseEntity<String> response = preflight("/mcp", FRONT,
                List.of("authorization", "content-type", "mcp-protocol-version"));

        // Cliente MCP nativo não passa por CORS, mas um em navegador nem chega a mandar a
        // requisição se o preflight recusar o cabeçalho de versão do protocolo.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowHeaders())
                .contains("mcp-protocol-version");

        // Controle negativo: o preflight continua 200, mas o cabeçalho fora da lista não volta
        // autorizado — é a resposta, e não o status, que o navegador usa para barrar. Sem isto a
        // asserção acima passaria mesmo que a allowlist ignorasse o que recebe.
        assertThat(preflight("/mcp", FRONT, List.of("authorization", "x-inventado"))
                .getHeaders().getAccessControlAllowHeaders())
                .doesNotContain("x-inventado");
    }

    private ResponseEntity<String> preflight(String path, String origin, List<String> requestHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(requestHeaders);
        return rest.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }
}
