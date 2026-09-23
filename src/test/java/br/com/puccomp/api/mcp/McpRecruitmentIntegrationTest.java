package br.com.puccomp.api.mcp;

import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recrutamento é o módulo que motivou o servidor MCP: é dele que vêm as perguntas que hoje obrigam
 * o membro a sair da ferramenta que já está usando.
 *
 * <p>Cada caso confere a resposta da ferramenta contra o endpoint REST irmão, com o mesmo recorte.
 * A falha que interessa não é "o número está errado" — é a ferramenta responder um conjunto e a
 * tela responder outro, que ninguém percebe até alguém decidir alguma coisa com o número errado.
 */
@Import(TestSeeder.class)
class McpRecruitmentIntegrationTest extends AbstractIntegrationTest {

    private static final Instant DIA_1 = Instant.parse("2026-03-01T15:00:00Z");
    private static final Instant DIA_2 = Instant.parse("2026-03-02T15:00:00Z");
    private static final Instant DIA_2_TARDE = Instant.parse("2026-03-02T18:00:00Z");

    @Autowired
    private TestSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private String token;
    private String pat;
    private UUID processId;
    private UUID computacao;
    private UUID design;

    @BeforeEach
    void setUp() {
        String slug = "ej-mcp-rec-" + UUID.randomUUID().toString().substring(0, 8);
        tenantId = seeder.seedTenant("EJ Recrutamento MCP", slug);
        seeder.seedAccount(tenantId, "dono@" + slug + ".dev", "senha123", Standing.OWNER);
        token = login("dono@" + slug + ".dev", "senha123");
        pat = criarPat(token, null);

        processId = abrirProcesso("PS 2026.1");
        computacao = primeiroCurso();
        design = seeder.seedCourse(tenantId, "Design");

        inscrever("Ana Alves", "ana@example.com", computacao, (short) 2, DIA_1);
        inscrever("Bruno Costa", "bruno@example.com", computacao, (short) 6, DIA_2);
        inscrever("Carla Dias", "carla@example.com", design, (short) 4, DIA_2_TARDE);
    }

    @Test
    @DisplayName("o agente encontra o processo aberto e a contagem de inscrições")
    void shouldListOpenProcessWithCounts() {
        String resposta = texto(chamar("recruitment_processes_list", Map.of("status", "OPEN")));

        assertThat(resposta).contains("PS 2026.1");
        assertThat(campo(resposta, "total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a listagem de inscrições devolve o mesmo conjunto que a rota REST irmã")
    void shouldMatchRestListing() {
        String viaMcp = texto(chamar("recruitment_applications_list",
                Map.of("process_id", processId.toString())));
        JsonNode viaRest = json("/v1/recruitment/applications?process_id=" + processId);

        // O envelope difere de propósito — o REST publica {content, page:{total_elements,...}} e a
        // ferramenta publica {items, total, page, pages}, que custa menos contexto ao agente. O que
        // não pode diferir é o conjunto de linhas.
        assertThat(campo(viaMcp, "total").asLong())
                .isEqualTo(viaRest.get("page").get("total_elements").asLong())
                .isEqualTo(3);
        assertThat(viaMcp).contains("Ana Alves").contains("Bruno Costa").contains("Carla Dias");
    }

    @Test
    @DisplayName("os filtros do recrutamento chegam à consulta: busca, curso e período")
    void shouldApplyRecruitmentFilters() {
        assertThat(campo(texto(chamar("recruitment_applications_list", Map.of("q", "Ana"))), "total")
                .asLong()).isEqualTo(1);

        assertThat(campo(texto(chamar("recruitment_applications_list",
                Map.of("course_id", design.toString()))), "total").asLong()).isEqualTo(1);

        // min_term=4 pega Carla (4) e Bruno (6), e deixa Ana (2) de fora.
        assertThat(campo(texto(chamar("recruitment_applications_list",
                Map.of("min_term", 4))), "total").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("o funil do processo bate com o resumo REST, e traz a curva de chegada")
    void shouldMatchRestFunnel() {
        String viaMcp = texto(chamar("recruitment_process_funnel",
                Map.of("process_id", processId.toString())));
        JsonNode viaRest = json("/v1/recruitment/processes/" + processId + "/applications/summary");

        assertThat(campo(viaMcp, "total").get("value").asLong())
                .isEqualTo(viaRest.get("total").get("value").asLong())
                .isEqualTo(3);
        // A curva diária é o que só existe na visão de dentro do prazo — dois dias, com pico no 02.
        assertThat(viaMcp).contains("by_day").contains("2026-03-02");
    }

    @Test
    @DisplayName("o resumo da EJ inteira conta pessoas, e não inscrições")
    void shouldCountDistinctPeopleAcrossProcesses() {
        UUID outro = abrirProcesso("PS 2026.2");
        inscrever("Ana Alves", "ana@example.com", computacao, (short) 3, DIA_2, outro);

        String resposta = texto(chamar("recruitment_applications_summary", Map.of()));

        // Quatro inscrições, três pessoas: a Ana voltou. É a pergunta que nenhum processo isolado
        // responde, e a razão de esta ferramenta existir separada do funil.
        assertThat(campo(resposta, "total").get("value").asLong()).isEqualTo(4);
        assertThat(campo(resposta, "candidates").get("distinct").asLong()).isEqualTo(3);
        assertThat(campo(resposta, "candidates").get("returning").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("processo inexistente: o funil avisa, a listagem devolve vazio")
    void shouldDistinguishUnknownProcessFromNoApplications() {
        String inventado = UUID.randomUUID().toString();

        // O funil herda o 404 da rota REST e vira erro legível de ferramenta.
        assertThat(chamar("recruitment_process_funnel", Map.of("process_id", inventado)))
                .contains("\"isError\":true").contains("não encontrado");

        // A listagem é uma busca filtrada, então id desconhecido é conjunto vazio, não erro. A
        // diferença está na descrição das duas ferramentas para o agente não ler uma como a outra.
        String listagem = chamar("recruitment_applications_list", Map.of("process_id", inventado));
        assertThat(listagem).doesNotContain("\"isError\":true");
        assertThat(campo(texto(listagem), "total").asLong()).isZero();
    }

    @Test
    @DisplayName("o recrutamento de outra EJ não aparece, nem pelo id do processo")
    void shouldIsolateRecruitmentBetweenTenants() {
        UUID outraEj = seeder.seedTenant("EJ Vizinha", "ej-vizinha-" + UUID.randomUUID().toString().substring(0, 8));
        seeder.seedAccount(outraEj, "dono-vizinha@ej.dev", "senha123", Standing.OWNER);
        String patVizinho = criarPat(login("dono-vizinha@ej.dev", "senha123"), null);

        String listagem = chamarCom(patVizinho, "recruitment_applications_list",
                Map.of("process_id", processId.toString()));

        assertThat(listagem).doesNotContain("Ana Alves").doesNotContain("PS 2026.1");
        assertThat(chamarCom(patVizinho, "recruitment_processes_list", Map.of()))
                .doesNotContain("PS 2026.1");
    }

    @Test
    @DisplayName("a listagem descreve o currículo sem assinar URL, que não teria uso nas mãos do agente")
    void shouldDescribeCvWithoutSigningIt() {
        UUID fileId = UUID.randomUUID();
        jdbc.update("""
                insert into stored_files (id, tenant_id, filename, content_type, size, bucket, object_key, state)
                values (?, ?, 'curriculo-ana.pdf', 'application/pdf', 2048, 'bucket-teste', ?, 'READY')
                """, fileId, tenantId, tenantId + "/files/" + fileId + ".pdf");
        jdbc.update("update candidate_applications set cv_file_id = ? where tenant_id = ? and email = ?",
                fileId, tenantId, "ana@example.com");

        // O armazenamento está desligado neste teste: assinar aqui responderia 503, e não a lista.
        String listagem = texto(chamar("recruitment_applications_list", Map.of("q", "ana")));

        assertThat(listagem).contains("curriculo-ana.pdf", "content_type")
                .doesNotContain("download_url", "download_expires_at");
    }

    @Test
    @DisplayName("sem recruitment:read o escopo fecha o módulo inteiro")
    void shouldRequireRecruitmentScope() {
        String estreito = criarPat(token, List.of("members:read"));

        assertThat(chamarCom(estreito, "recruitment_applications_list", Map.of()))
                .contains("\"isError\":true");
        assertThat(chamarCom(estreito, "recruitment_process_funnel",
                Map.of("process_id", processId.toString()))).contains("\"isError\":true");
        assertThat(chamarCom(estreito, "recruitment_applications_summary", Map.of()))
                .contains("\"isError\":true");
    }

    private String chamar(String ferramenta, Map<String, Object> argumentos) {
        return chamarCom(pat, ferramenta, argumentos);
    }

    private String chamarCom(String credencial, String ferramenta, Map<String, Object> argumentos) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setBearerAuth(credencial);
        Map<String, Object> payload = Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call",
                "params", Map.of("name", ferramenta, "arguments", argumentos));
        ResponseEntity<String> res = rest.exchange("/mcp", HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    /** O conteúdo da ferramenta chega como texto dentro do envelope JSON-RPC. */
    private String texto(String respostaMcp) {
        JsonNode content = mapper.readTree(respostaMcp).get("result").get("content");
        assertThat(content).as("conteúdo da ferramenta em %s", respostaMcp).isNotNull();
        return content.get(0).get("text").asString();
    }

    private JsonNode campo(String jsonDaFerramenta, String nome) {
        JsonNode valor = mapper.readTree(jsonDaFerramenta).get(nome);
        assertThat(valor).as("campo %s em %s", nome, jsonDaFerramenta).isNotNull();
        return valor;
    }

    private JsonNode json(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return mapper.readTree(rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody());
    }

    private String criarPat(String ownerToken, List<String> scopes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("name", "agente");
        body.put("scopes", scopes);
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/auth/pat", HttpMethod.POST,
                new HttpEntity<>(body, headers), new ParameterizedTypeReference<Map<String, Object>>() { });
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) res.getBody().get("token");
    }

    private UUID abrirProcesso(String titulo) {
        UUID id = post("/v1/recruitment/processes",
                new SelectionProcessRequest(titulo, null, null, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + id + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);
        return id;
    }

    private UUID primeiroCurso() {
        return get("/v1/courses", token,
                new ParameterizedTypeReference<List<CourseCatalog.CourseOption>>() { })
                .getBody().getFirst().id();
    }

    private void inscrever(String nome, String email, UUID courseId, Short periodo, Instant quando) {
        inscrever(nome, email, courseId, periodo, quando, processId);
    }

    private void inscrever(String nome, String email, UUID courseId, Short periodo, Instant quando,
                           UUID processo) {
        jdbc.update("""
                insert into candidate_applications (id, tenant_id, process_id, full_name, email, phone,
                    course_id, current_term, privacy_consent_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, '31999998888', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, processo, nome, email, courseId, periodo,
                Timestamp.from(quando), Timestamp.from(quando), Timestamp.from(quando));
    }
}
