package br.com.puccomp.api.mcp;

import br.com.puccomp.api.organization.departments.DepartmentResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class McpServerIntegrationTest extends AbstractIntegrationTest {

    private static final String[] FERRAMENTAS = {
            "members_list", "members_get", "members_summary",
            "roles_list", "roles_get",
            "departments_list", "departments_get",
            "courses_list",
            "whoami",
            "recruitment_processes_list", "recruitment_processes_get",
            "recruitment_applications_list", "recruitment_process_funnel",
            "recruitment_applications_summary",
            "financial_entries_list", "financial_entries_get", "financial_summary"
    };

    @Autowired
    private TestSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

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
    @DisplayName("a sequência que um cliente real executa ao conectar funciona inteira")
    void shouldCompleteTheClientHandshake() {
        String pat = patDe("EJ MCP aperto", "ej-mcp-aperto", "dono-aperto@ej.dev", null);

        // A spec de 2026-07-28 aposentou o handshake, mas os clientes de hoje ainda o executam, e
        // um servidor que recusasse qualquer um destes passos simplesmente não conectaria.
        ResponseEntity<String> initialize = mcp(pat, jsonRpc(1, "initialize", Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "teste", "version", "1"))));
        assertThat(initialize.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initialize.getBody()).contains("protocolVersion").contains("puccomp-api");
        // As instruções do servidor são o primeiro texto que o agente lê sobre a EJ.
        assertThat(initialize.getBody()).contains("Empresa Júnior");

        Map<String, Object> iniciado = new HashMap<>();
        iniciado.put("jsonrpc", "2.0");
        iniciado.put("method", "notifications/initialized");
        assertThat(mcp(pat, iniciado).getStatusCode().is2xxSuccessful())
                .as("notificação sem id precisa ser aceita, não recusada")
                .isTrue();

        assertThat(mcp(pat, jsonRpc(2, "ping", null)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mcp(pat, jsonRpc(3, "tools/list", null)).getBody()).contains("members_list");
        assertThat(mcp(pat, chamada(4, "members_list", Map.of())).getBody())
                .contains("dono-aperto@ej.dev");
    }

    @Test
    @DisplayName("whoami orienta o agente: qual EJ, quem, e o que o escopo deixou passar")
    void shouldTellTheAgentWhoItIs() {
        String pat = patDe("EJ MCP identidade", "ej-mcp-identidade", "dono-id@ej.dev",
                List.of("members:read"));

        String resposta = mcp(pat, chamada(12, "whoami", Map.of())).getBody();

        assertThat(resposta).contains("EJ MCP identidade").contains("dono-id@ej.dev");
        // O dono tem todas as permissões, mas o escopo do token recortou: é justamente esta lista
        // que permite ao agente dizer "falta financial:read" em vez de repassar um Access Denied.
        assertThat(resposta).contains("members:read").doesNotContain("financial:read");
    }

    @Test
    @DisplayName("o catálogo publicado é exatamente este, e mudá-lo é uma decisão consciente")
    void shouldPublishTheAgreedToolRoster() {
        String pat = patDe("EJ MCP catálogo", "ej-mcp-catalogo", "dono-catalogo@ej.dev", null);

        ResponseEntity<String> res = mcp(pat, jsonRpc(1, "tools/list", null));

        // Cada ferramenta nova é superfície que o agente relê a cada conversa, e contexto que ele
        // paga. A lista está aqui para que acrescentar uma passe por uma linha de teste.
        assertThat(res.getBody()).contains(FERRAMENTAS);
        assertThat(quantasFerramentas(res.getBody())).isEqualTo(FERRAMENTAS.length);
    }

    @Test
    @DisplayName("a superfície é snake_case nas duas direções, igual à API REST")
    void shouldSpeakSnakeCaseBothWays() {
        String pat = patDe("EJ MCP caixa", "ej-mcp-caixa", "dono-caixa@ej.dev", null);

        // Saída: o Spring AI serializa o retorno com um mapper estático próprio, que ignora a
        // configuração do Spring. Sem serializarmos nós, sairia activeHeadcount aqui e
        // active_headcount no REST — e as descrições das ferramentas citam os nomes do REST.
        assertThat(chavesCamelCase(mcp(pat, chamada(7, "members_summary", Map.of())).getBody()))
                .isEmpty();
        assertThat(mcp(pat, chamada(8, "members_summary", Map.of())).getBody())
                .contains("active_headcount").contains("organization_context");
        assertThat(chavesCamelCase(mcp(pat, chamada(9, "financial_summary", Map.of())).getBody()))
                .isEmpty();

        // Entrada: o nome do parâmetro Java vira o nome publicado no schema e o nome que o agente
        // manda de volta em arguments. Os dois saem do mesmo lugar, então basta olhar o catálogo.
        String catalogo = mcp(pat, jsonRpc(10, "tools/list", null)).getBody();
        assertThat(catalogo).contains("\"department_id\"").contains("\"min_term\"")
                .contains("\"has_cv\"").contains("\"process_id\"")
                .contains("\"slice_limit\"").contains("\"turnover_months\"")
                .contains("\"has_role\"").contains("\"has_department\"");
        assertThat(catalogo).doesNotContain("departmentId").doesNotContain("minTerm")
                .doesNotContain("hasCv").doesNotContain("processId")
                .doesNotContain("sliceLimit").doesNotContain("turnoverMonths")
                .doesNotContain("hasRole").doesNotContain("hasDepartment");
    }

    @Test
    @DisplayName("o argumento snake_case realmente filtra, e não é aceito e ignorado")
    void shouldBindSnakeCaseArguments() {
        UUID tenant = seeder.seedTenant("EJ MCP bind", "ej-mcp-bind");
        seeder.seedAccount(tenant, "dono-bind@ej.dev", "senha123", Standing.OWNER);
        UUID cargo = seeder.seedCargo(tenant, "Diretor de Bind");
        seeder.seedAccount(tenant, "com-cargo@ej.dev", "senha123", Standing.MEMBER, cargo);
        // Dois cargos distintos, senão não há cauda para slice_limit cortar mais abaixo.
        UUID outroCargo = seeder.seedCargo(tenant, "Analista de Bind");
        seeder.seedAccount(tenant, "outro-cargo@ej.dev", "senha123", Standing.MEMBER, outroCargo);

        String pat = criarPat(login("dono-bind@ej.dev", "senha123"), null);
        String comFiltro = mcp(pat, chamada(11, "members_list",
                Map.of("role_id", cargo.toString()))).getBody();

        // Um nome que o schema publica mas a vinculação ignora passaria despercebido: a chamada
        // responderia 200 com o quadro inteiro, e o agente concluiria que o filtro não achou nada.
        assertThat(comFiltro).contains("com-cargo@ej.dev").doesNotContain("dono-bind@ej.dev");

        // q é o que torna a superfície útil para um agente: sem ele, achar alguém pelo nome
        // exigiria paginar o quadro inteiro e comparar strings do lado de fora.
        String porNome = mcp(pat, chamada(12, "members_list",
                Map.of("q", "com-cargo"))).getBody();
        assertThat(porNome).contains("com-cargo@ej.dev").doesNotContain("dono-bind@ej.dev");

        // E o corte de cauda precisa mesmo cortar: aceito e ignorado, a resposta cresceria em
        // silêncio a cada cargo novo da EJ.
        String cortado = mcp(pat, chamada(13, "members_summary",
                Map.of("slice_limit", 1))).getBody();
        assertThat(cortado).contains("OTHERS");
    }

    @Test
    @DisplayName("has_role e has_department recortam na ferramenta o mesmo conjunto que no REST")
    void shouldMatchRestOnPresenceFilters() {
        UUID tenant = seeder.seedTenant("EJ MCP presença", "ej-mcp-presenca");
        seeder.seedAccount(tenant, "dono@presenca.dev", "senha123", Standing.OWNER);
        UUID cargo = seeder.seedCargo(tenant, "Cargo de Presença");
        seeder.seedAccount(tenant, "com-cargo@presenca.dev", "senha123", Standing.MEMBER, cargo);
        UUID lotado = seeder.seedAccount(tenant, "com-diretoria@presenca.dev", "senha123", Standing.MEMBER);
        String owner = login("dono@presenca.dev", "senha123");
        UUID diretoria = post("/v1/departments", Map.of("name", "Projetos", "description", "Projetos"),
                owner, DepartmentResponse.class).getBody().id();
        assertThat(put("/v1/members/" + lotado + "/assignment",
                Map.of("department_id", diretoria.toString()), owner, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String pat = criarPat(owner, null);

        for (Map<String, Object> recorte : List.<Map<String, Object>>of(
                Map.of("has_role", true),
                Map.of("has_department", true),
                Map.of("has_role", false, "has_department", false))) {
            String query = recorte.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
            List<String> pelaApi = emails(json(owner, "/v1/members?" + query).get("content"));

            assertThat(pelaApi).as("recorte %s", recorte).hasSize(1);
            assertThat(emails(resultado(pat, "members_list", recorte).get("items")))
                    .as("members_list %s", recorte).isEqualTo(pelaApi);
            assertThat(resultado(pat, "members_summary", recorte).get("total").get("value").asInt())
                    .as("members_summary %s", recorte).isEqualTo(pelaApi.size());
        }

        // Contradição é recusada, não respondida com zero: o agente precisa saber que a pergunta
        // não descrevia conjunto nenhum.
        assertThat(mcp(pat, chamada(20, "members_list",
                Map.of("has_role", false, "role_id", cargo.toString()))).getBody())
                .contains("\"isError\":true").contains("has_role=false não combina com role_id");
    }

    @Test
    @DisplayName("sort ordena como o REST, e quem não tem data de entrada vai para o fim")
    void shouldSortLikeRest() {
        UUID tenant = seeder.seedTenant("EJ MCP ordem", "ej-mcp-ordem");
        seeder.seedAccount(tenant, "dono@ordem.dev", "senha123", Standing.OWNER);
        UUID antigo = seeder.seedAccount(tenant, "antigo@ordem.dev", "senha123", Standing.MEMBER);
        UUID recente = seeder.seedAccount(tenant, "recente@ordem.dev", "senha123", Standing.MEMBER);
        UUID baseline = seeder.seedAccount(tenant, "baseline@ordem.dev", "senha123", Standing.MEMBER);
        entrada(antigo, "2024-03-01T12:00:00Z");
        entrada(recente, "2026-08-01T12:00:00Z");
        entrada(baseline, null);
        String owner = login("dono@ordem.dev", "senha123");
        String pat = criarPat(owner, null);

        assertThat(mcp(pat, jsonRpc(21, "tools/list", null)).getBody())
                .contains("JOINED_AT_DESC", "LEFT_AT_DESC");

        List<String> recentes = emails(resultado(pat, "members_list",
                Map.of("standing", "MEMBER", "sort", "JOINED_AT_DESC")).get("items"));

        // Com o nulo primeiro, "quem entrou por último" começaria por quem já estava na EJ.
        assertThat(recentes)
                .containsExactly("recente@ordem.dev", "antigo@ordem.dev", "baseline@ordem.dev");
        assertThat(recentes).isEqualTo(
                emails(json(owner, "/v1/members?standing=MEMBER&sort=joined_at,desc").get("content")));
    }

    @Test
    @DisplayName("o escopo recorta por módulo: members:read não abre o financeiro")
    void shouldScopeToolsPerModule() {
        String pat = patDe("EJ MCP módulos", "ej-mcp-modulos", "dono-modulos@ej.dev",
                List.of("members:read"));

        assertThat(mcp(pat, chamada(5, "members_list", Map.of())).getBody())
                .doesNotContain("\"isError\":true");
        assertThat(mcp(pat, chamada(6, "financial_entries_list", Map.of())).getBody())
                .contains("\"isError\":true");
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

    private final ObjectMapper mapper = new ObjectMapper();

    /** O resultado da ferramenta chega como texto JSON dentro do envelope JSON-RPC. */
    private JsonNode resultado(String pat, String tool, Map<String, Object> argumentos) {
        JsonNode content = mapper.readTree(mcp(pat, chamada(30, tool, argumentos)).getBody())
                .get("result").get("content");
        return mapper.readTree(content.get(0).get("text").asString());
    }

    private JsonNode json(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return mapper.readTree(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getBody());
    }

    private void entrada(UUID member, String quando) {
        jdbc.update("update members set joined_at = ? where id = ?",
                quando == null ? null : Timestamp.from(Instant.parse(quando)), member);
    }

    private static List<String> emails(JsonNode members) {
        List<String> emails = new ArrayList<>();
        for (JsonNode member : members) emails.add(member.get("email").asString());
        return emails;
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

    private static int quantasFerramentas(String body) {
        return body.split("\"inputSchema\"", -1).length - 1;
    }

    /**
     * Só as chaves de dentro do resultado da ferramenta, que chegam com aspas escapadas por estarem
     * num campo de texto. O envelope JSON-RPC em volta — {@code isError}, {@code inputSchema} — é
     * camelCase por especificação, e não é nosso para mudar.
     */
    private static List<String> chavesCamelCase(String body) {
        var encontradas = new ArrayList<String>();
        var matcher = Pattern.compile("\\\\\"([a-z][a-z0-9]*[A-Z][A-Za-z0-9]*)\\\\\"").matcher(body);
        while (matcher.find()) encontradas.add(matcher.group(1));
        return encontradas;
    }
}
