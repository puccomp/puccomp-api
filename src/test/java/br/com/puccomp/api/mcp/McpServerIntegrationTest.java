package br.com.puccomp.api.mcp;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class McpServerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("o endpoint MCP nasce autenticado: sem credencial é 401")
    void shouldRequireCredentials() {
        ResponseEntity<String> res = mcp(null, jsonRpc(1, "tools/list", null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("tools/list publica as ferramentas sem handshake e sem abrir sessão")
    void shouldPublishMemberTools() {
        String pat = patDe("EJ MCP lista", "ej-mcp-lista", "dono-lista@ej.dev", null);

        ResponseEntity<String> res = mcp(pat, jsonRpc(1, "tools/list", null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("members_list").contains("members_get");
        // A chamada valeu sem o initialize que a spec antiga exigia, e nada de sessão volta para
        // ser reapresentado depois: é o protocolo STATELESS, verificado em vez de suposto.
        assertThat(res.getHeaders().headerNames()).doesNotContain("Mcp-Session-Id");
    }

    @Test
    @DisplayName("ferramenta só enxerga a EJ do token, nunca a de outro tenant")
    void shouldIsolateTenants() {
        UUID tenantA = seeder.seedTenant("EJ MCP A", "ej-mcp-a");
        UUID tenantB = seeder.seedTenant("EJ MCP B", "ej-mcp-b");
        seeder.seedAccount(tenantA, "dono-mcp-a@ej.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenantB, "dono-mcp-b@ej.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenantB, "so-na-b@ej.dev", "senha123", Standing.MEMBER);

        String pat = criarPat(login("dono-mcp-a@ej.dev", "senha123"), null);
        ResponseEntity<String> res = mcp(pat, chamada(2, "members_list", Map.of()));

        // O TenantContext é ThreadLocal e o Hibernate cai num tenant zerado em silêncio quando ele
        // não está preenchido: em ASYNC, ou fora da thread da requisição, este teste seria a única
        // coisa entre o desenho e um vazamento entre EJs.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("dono-mcp-a@ej.dev").doesNotContain("so-na-b@ej.dev");
    }

    @Test
    @DisplayName("escopo do token recorta a ferramenta: sem members:read, members_list nega")
    void shouldEnforceScopeOnTools() {
        String pat = patDe("EJ MCP escopo", "ej-mcp-escopo", "dono-escopo-mcp@ej.dev",
                List.of("financial:read"));

        ResponseEntity<String> res = mcp(pat, chamada(3, "members_list", Map.of()));

        // O @PreAuthorize da ferramenta precisa valer no caminho do MCP como vale no do controller;
        // se o scanner do Spring AI guardasse o alvo em vez do proxy, a recusa sumiria em silêncio.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"isError\":true");
        assertThat(res.getBody()).doesNotContain("dono-escopo-mcp@ej.dev");
    }

    @Test
    @DisplayName("token revogado deixa de abrir o endpoint")
    void shouldRejectRevokedToken() {
        UUID tenant = seeder.seedTenant("EJ MCP revogado", "ej-mcp-revogado");
        seeder.seedAccount(tenant, "dono-revogado@ej.dev", "senha123", Standing.OWNER);
        String owner = login("dono-revogado@ej.dev", "senha123");

        Map<String, Object> criado = criarPatCompleto(owner, null);
        String pat = (String) criado.get("token");
        assertThat(mcp(pat, jsonRpc(1, "tools/list", null)).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> revogado = delete("/v1/auth/pat/" + criado.get("id"), owner, Void.class);
        assertThat(revogado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(mcp(pat, jsonRpc(1, "tools/list", null)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("erro de domínio chega ao agente como mensagem, não como stacktrace")
    void shouldReportDomainErrorsAsToolErrors() {
        String pat = patDe("EJ MCP erro", "ej-mcp-erro", "dono-erro@ej.dev", null);

        ResponseEntity<String> res = mcp(pat,
                chamada(4, "members_get", Map.of("id", UUID.randomUUID().toString())));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"isError\":true").contains("não encontrado");
        assertThat(res.getBody()).doesNotContain("br.com.puccomp.api.shared.exception");
    }

    private String patDe(String nome, String slug, String email, List<String> scopes) {
        UUID tenant = seeder.seedTenant(nome, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        return criarPat(login(email, "senha123"), scopes);
    }

    private String criarPat(String ownerToken, List<String> scopes) {
        return (String) criarPatCompleto(ownerToken, scopes).get("token");
    }

    private Map<String, Object> criarPatCompleto(String ownerToken, List<String> scopes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("name", "agente");
        body.put("scopes", scopes);
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/auth/pat", HttpMethod.POST,
                new HttpEntity<>(body, headers), new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private ResponseEntity<String> mcp(String pat, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        if (pat != null) headers.setBearerAuth(pat);
        return rest.exchange("/mcp", HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
    }

    private static Map<String, Object> jsonRpc(int id, String method, Map<String, Object> params) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        if (params != null) payload.put("params", params);
        return payload;
    }

    private static Map<String, Object> chamada(int id, String tool, Map<String, Object> argumentos) {
        return jsonRpc(id, "tools/call", Map.of("name", tool, "arguments", argumentos));
    }
}
