package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * O resumo do quadro tem duas populações de propósito, e é aí que ele erra: a composição descreve
 * o conjunto filtrado, o contexto descreve a EJ inteira. Filtrar por curso não pode abrir vagas
 * nem esvaziar diretorias — e um cargo cheio não pode "abrir" porque só um ocupante casou com o filtro.
 */
@Import(TestSeeder.class)
class MemberSummaryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private String token;
    private String slug;

    private UUID computacao;
    private UUID design;
    private UUID comercial;
    private UUID projetos;
    private UUID marketing;
    private UUID diretorComercial;
    private UUID analista;
    private UUID trainee;
    private UUID presidente;
    private UUID vago;
    private UUID zerado;
    private UUID cargoInativo;

    @BeforeEach
    void setUp() {
        slug = "ej-quadro-" + UUID.randomUUID().toString().substring(0, 8);
        tenantId = seeder.seedTenant("EJ Quadro", slug);
        seeder.seedAccount(tenantId, "dono@" + slug + ".dev", "senha123", Standing.OWNER);
        token = login("dono@" + slug + ".dev", "senha123");

        computacao = firstCourse();
        design = seeder.seedCourse(tenantId, "Design");

        comercial = createDepartment("Comercial");
        projetos = createDepartment("Projetos");
        marketing = createDepartment("Marketing");

        diretorComercial = createRole("Diretor Comercial", comercial, 1);
        analista = createRole("Analista", projetos, 5);
        trainee = createRole("Trainee", projetos, null);
        presidente = createRole("Presidente", null, 1);
        vago = createRole("Vago", null, 2);
        zerado = createRole("Zerado", null, 1);
        cargoInativo = createRole("Cargo Inativo", comercial, 3);

        // Capacidade zero e cargo desativado não passam pela API de escrita, e são justamente os
        // dois casos que o contrato distingue de "capacidade desconhecida".
        jdbc.update("update roles set max_seats = 0 where id = ?", zerado);
        jdbc.update("update roles set active = false where id = ?", cargoInativo);

        member("Ana Ativa", MemberStatus.ACTIVE, computacao, diretorComercial, comercial);
        member("Bruno Analista", MemberStatus.ACTIVE, computacao, analista, projetos);
        member("Carla Analista", MemberStatus.ACTIVE, design, analista, projetos);
        member("Diego Alumni", MemberStatus.ALUMNUS, design, null, null);
        member("Elisa Inativa", MemberStatus.ACTIVE, design, cargoInativo, comercial);
        member("Fabio Afastado", MemberStatus.INACTIVE, computacao, analista, projetos);
        member("Gabi Presidente", MemberStatus.ACTIVE, computacao, presidente, null);
        member("Hugo Presidente", MemberStatus.ACTIVE, computacao, presidente, null);
        member("Ivan Trainee", MemberStatus.ACTIVE, design, trainee, projetos);
    }

    @Test
    @DisplayName("total e active_headcount descrevem a população filtrada, e batem com a listagem")
    void shouldCountTheFilteredPopulation() {
        JsonNode resumo = summary("");
        assertThat(resumo.path("total").path("value").asInt()).isEqualTo(10);
        assertThat(resumo.path("total").path("previous").isNull()).isTrue();
        assertThat(resumo.path("active_headcount").path("value").asInt()).isEqualTo(8);
        assertThat(resumo.path("active_headcount").path("previous").isNull()).isTrue();

        assertParity("");
        assertParity("?course_id=" + design);
        assertParity("?role_id=" + analista);
        assertParity("?department_id=" + projetos);
        assertParity("?status=ACTIVE");
        assertParity("?standing=OWNER");
        assertParity("?has_role=false");
        assertParity("?has_department=false");
        assertParity("?course_id=" + design + "&status=ACTIVE&has_department=true");
    }

    @Test
    @DisplayName("page, size e sort não alteram o resumo")
    void shouldIgnorePagination() {
        assertThat(summary("?page=1&size=1&sort=name,desc")).isEqualTo(summary(""));
    }

    @Test
    @DisplayName("cada distribuição soma o total, inclusive a categoria sem vínculo")
    void shouldKeepDistributionsExhaustive() {
        JsonNode resumo = summary("");

        for (String field : List.of("by_department", "by_role", "by_course", "by_status", "by_standing")) {
            assertThat(sumOf(resumo.path(field))).as(field).isEqualTo(10);
            assertThat(sumOfShares(resumo.path(field))).as(field).isCloseTo(1d, within(1e-9));
        }

        // Quatro membros sem diretoria continuam na distribuição, como categoria de id nulo.
        JsonNode semDiretoria = categoryWithNullId(resumo.path("by_department"));
        assertThat(semDiretoria.path("count").asInt()).isEqualTo(4);
        assertThat(semDiretoria.path("key").path("name").asText()).isEqualTo("Sem diretoria");
        assertThat(categoryWithNullId(resumo.path("by_role")).path("count").asInt()).isEqualTo(2);

        // Elisa está num cargo desativado: o cargo sai da capacidade, ela não sai da composição.
        assertThat(countOf(resumo.path("by_role"), cargoInativo.toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("distribuições de enum saem na ordem declarada, com rótulo em português")
    void shouldOrderEnumDistributionsByDeclaration() {
        JsonNode resumo = summary("");

        assertThat(ids(resumo.path("by_status"))).containsExactly("ACTIVE", "ALUMNUS", "INACTIVE");
        assertThat(resumo.path("by_status").get(0).path("key").path("name").asText()).isEqualTo("Ativo");
        assertThat(resumo.path("by_status").get(0).path("count").asInt()).isEqualTo(8);

        assertThat(ids(resumo.path("by_standing"))).containsExactly("OWNER", "MEMBER");
        assertThat(resumo.path("by_standing").get(0).path("count").asInt()).isEqualTo(1);
        assertThat(resumo.path("by_standing").get(1).path("count").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("distribuições por recurso vêm por contagem decrescente, com nulo por último no empate")
    void shouldOrderReferenceDistributions() {
        JsonNode byRole = summary("").path("by_role");

        assertThat(byRole.get(0).path("key").path("id").asText()).isEqualTo(analista.toString());
        assertThat(byRole.get(0).path("count").asInt()).isEqualTo(3);
        // Presidente e "sem cargo" empatam em 2; o identificado vem antes do nulo.
        assertThat(byRole.get(1).path("key").path("id").asText()).isEqualTo(presidente.toString());
        assertThat(byRole.get(2).path("key").path("id").isNull()).isTrue();
    }

    @Test
    @DisplayName("as lacunas contam só ativos do recorte: status=ALUMNUS zera as duas")
    void shouldCountGapsAmongActiveMembersOnly() {
        JsonNode resumo = summary("");
        assertThat(resumo.path("gaps").path("without_role").asInt()).isEqualTo(1);
        assertThat(resumo.path("gaps").path("without_department").asInt()).isEqualTo(3);

        // Diego é alumnus e não tem cargo, mas não é uma lacuna do quadro ativo.
        JsonNode alumni = summary("?status=ALUMNUS");
        assertThat(alumni.path("total").path("value").asInt()).isEqualTo(1);
        assertThat(alumni.path("gaps").path("without_role").asInt()).isZero();
        assertThat(alumni.path("gaps").path("without_department").asInt()).isZero();
    }

    @Test
    @DisplayName("capacidade descreve a EJ inteira: nenhum filtro de membro a altera")
    void shouldKeepOrganizationContextOutOfTheFilter() {
        JsonNode semFiltro = summary("").path("organization_context");
        JsonNode porCurso = summary("?course_id=" + design).path("organization_context");
        JsonNode porCargo = summary("?role_id=" + analista).path("organization_context");
        JsonNode nadaCorresponde = summary("?course_id=" + UUID.randomUUID()).path("organization_context");

        assertThat(porCurso).isEqualTo(semFiltro);
        assertThat(porCargo).isEqualTo(semFiltro);
        assertThat(nadaCorresponde).isEqualTo(semFiltro);
        assertThat(semFiltro.path("scope").asText()).isEqualTo("TENANT");
    }

    @Test
    @DisplayName("cargo cheio, acima da capacidade, vago, sem capacidade e inativo têm cada um seu caso")
    void shouldDescribeSeatsPerRole() {
        JsonNode seats = summary("").path("organization_context").path("seats");

        JsonNode cheio = seat(seats, diretorComercial);
        assertThat(cheio.path("occupied").asInt()).isEqualTo(1);
        assertThat(cheio.path("max").asInt()).isEqualTo(1);
        assertThat(cheio.path("open").asInt()).isZero();
        // Fabio ocupa Analista mas está INACTIVE: ocupação conta apenas quem está ativo.
        assertThat(seat(seats, analista).path("occupied").asInt()).isEqualTo(2);
        assertThat(seat(seats, analista).path("open").asInt()).isEqualTo(3);
        // Excesso de ocupação é preservado como negativo, não zerado.
        assertThat(seat(seats, presidente).path("open").asInt()).isEqualTo(-1);
        // Capacidade desconhecida não é zero nem ilimitada: max e open ficam nulos.
        assertThat(seat(seats, trainee).path("max").isNull()).isTrue();
        assertThat(seat(seats, trainee).path("open").isNull()).isTrue();
        assertThat(seat(seats, trainee).path("occupied").asInt()).isEqualTo(1);
        // Cargo sem ocupante continua listado — é exatamente a vaga a preencher.
        assertThat(seat(seats, vago).path("open").asInt()).isEqualTo(2);
        // Cargo desativado não oferece capacidade e não aparece aqui.
        assertThat(seats.path("by_role").findValuesAsText("id")).doesNotContain(cargoInativo.toString());

        assertThat(ids(seats.path("by_role"), "role")).isSorted();
    }

    @Test
    @DisplayName("os agregados de capacidade usam só cargos com limite conhecido e mantêm o saldo")
    void shouldAggregateOnlyRolesWithKnownCapacity() {
        JsonNode seats = summary("").path("organization_context").path("seats");

        assertThat(seats.path("total").asInt()).isEqualTo(9);
        assertThat(seats.path("occupied").asInt()).isEqualTo(5);
        assertThat(seats.path("open").asInt()).isEqualTo(4);
        assertThat(seats.path("open").asInt())
                .isEqualTo(seats.path("total").asInt() - seats.path("occupied").asInt());
        // Ivan ocupa o Trainee, cuja capacidade é desconhecida: fica fora do saldo, contado à parte.
        assertThat(seats.path("occupied_without_capacity").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("lacunas da estrutura: diretoria sem ativo e cargo sem ocupante, exceto capacidade zero")
    void shouldListStructuralGaps() {
        JsonNode contexto = summary("").path("organization_context");

        assertThat(contexto.path("empty_departments").findValuesAsText("id"))
                .containsExactly(marketing.toString());
        // Vago tem capacidade a preencher; Zerado declarou zero vagas e não é lacuna.
        assertThat(contexto.path("unfilled_roles").findValuesAsText("id"))
                .containsExactly(vago.toString());
        assertThat(contexto.path("unfilled_roles").findValuesAsText("id"))
                .doesNotContain(zerado.toString(), cargoInativo.toString());

        // Filtrar por um curso que ninguém do Comercial cursa não inventa uma diretoria vazia.
        assertThat(summary("?course_id=" + design).path("organization_context")
                .path("empty_departments").findValuesAsText("id"))
                .containsExactly(marketing.toString());
    }

    @Test
    @DisplayName("sem as permissões adicionais, os blocos de contexto vêm nulos sem revelar os dados")
    void shouldHideContextWithoutAdditionalPermissions() {
        String soMembros = tokenWithPermissions("Leitor de Membros", "members:read");
        JsonNode contexto = summary(soMembros, "").path("organization_context");

        assertThat(contexto.path("scope").asText()).isEqualTo("TENANT");
        assertThat(contexto.path("seats").isNull()).isTrue();
        assertThat(contexto.path("unfilled_roles").isNull()).isTrue();
        assertThat(contexto.path("empty_departments").isNull()).isTrue();

        String comDiretorias = tokenWithPermissions("Leitor de Diretorias", "members:read", "departments:read");
        JsonNode parcial = summary(comDiretorias, "").path("organization_context");
        assertThat(parcial.path("seats").isNull()).isTrue();
        assertThat(parcial.path("unfilled_roles").isNull()).isTrue();
        assertThat(parcial.path("empty_departments").findValuesAsText("id"))
                .containsExactly(marketing.toString());
    }

    @Test
    @DisplayName("filtro contraditório e alias divergente são 400 nos dois endpoints")
    void shouldRejectContradictoryFilters() {
        for (String query : List.of(
                "?has_role=false&role_id=" + analista,
                "?has_department=false&department_id=" + projetos,
                "?has_department=false&departmentId=" + projetos,
                "?department_id=" + projetos + "&departmentId=" + comercial,
                "?status=NAO_EXISTE",
                "?standing=NAO_EXISTE",
                "?course_id=nao-e-uuid",
                "?has_role=talvez")) {
            assertThat(getWithToken("/v1/members" + query, token).getStatusCode())
                    .as("listagem %s", query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(getWithToken("/v1/members/summary" + query, token).getStatusCode())
                    .as("resumo %s", query).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("departmentId continua valendo como alias, e concordar com department_id é aceito")
    void shouldAcceptTheDeprecatedAlias() {
        JsonNode canonico = summary("?department_id=" + projetos);
        assertThat(summary("?departmentId=" + projetos)).isEqualTo(canonico);
        assertThat(summary("?department_id=" + projetos + "&departmentId=" + projetos))
                .isEqualTo(canonico);
        assertThat(canonico.path("total").path("value").asInt()).isEqualTo(4);

        assertParity("?departmentId=" + projetos);
    }

    @Test
    @DisplayName("recorte vazio zera a composição sem apagar o contexto real da EJ")
    void shouldKeepContextWhenTheFilterMatchesNobody() {
        JsonNode resumo = summary("?course_id=" + UUID.randomUUID());

        assertThat(resumo.path("total").path("value").asInt()).isZero();
        assertThat(resumo.path("active_headcount").path("value").asInt()).isZero();
        assertThat(resumo.path("by_department")).isEmpty();
        assertThat(resumo.path("by_role")).isEmpty();
        assertThat(resumo.path("by_status")).isEmpty();
        assertThat(resumo.path("gaps").path("without_role").asInt()).isZero();
        assertThat(resumo.path("organization_context").path("seats").path("total").asInt()).isEqualTo(9);
        assertThat(resumo.path("organization_context").path("empty_departments")).isNotEmpty();
    }

    @Test
    @DisplayName("EJ sem membros nem estrutura devolve tudo zerado e vazio, nunca nulo")
    void shouldReturnEmptyShapeForAnEmptyOrganization() {
        String vazioSlug = "ej-vazia-" + UUID.randomUUID().toString().substring(0, 8);
        UUID vazioTenant = seeder.seedTenant("EJ Vazia", vazioSlug);
        seeder.seedAccount(vazioTenant, "dono@" + vazioSlug + ".dev", "senha123", Standing.OWNER);
        String vazioToken = login("dono@" + vazioSlug + ".dev", "senha123");
        // O próprio dono é um membro; sem ele a EJ não teria como ser consultada.
        jdbc.update("delete from member_status_history where tenant_id = ?", vazioTenant);
        jdbc.update("delete from members where tenant_id = ?", vazioTenant);

        JsonNode resumo = summary(vazioToken, "");
        assertThat(resumo.path("total").path("value").asInt()).isZero();
        assertThat(resumo.path("by_department")).isEmpty();
        assertThat(resumo.path("gaps").path("without_department").asInt()).isZero();

        JsonNode contexto = resumo.path("organization_context");
        assertThat(contexto.path("seats").path("total").asInt()).isZero();
        assertThat(contexto.path("seats").path("occupied").asInt()).isZero();
        assertThat(contexto.path("seats").path("open").asInt()).isZero();
        assertThat(contexto.path("seats").path("occupied_without_capacity").asInt()).isZero();
        assertThat(contexto.path("seats").path("by_role")).isEmpty();
        assertThat(contexto.path("empty_departments")).isEmpty();
        assertThat(contexto.path("unfilled_roles")).isEmpty();
    }

    @Test
    @DisplayName("outra EJ não aparece na composição nem nas consultas de capacidade e lacunas")
    void shouldIsolateTenants() {
        String outroSlug = "ej-vizinha-" + UUID.randomUUID().toString().substring(0, 8);
        UUID outroTenant = seeder.seedTenant("EJ Vizinha", outroSlug);
        seeder.seedAccount(outroTenant, "dono@" + outroSlug + ".dev", "senha123", Standing.OWNER);
        String outroToken = login("dono@" + outroSlug + ".dev", "senha123");

        JsonNode vizinha = summary(outroToken, "");
        assertThat(vizinha.path("total").path("value").asInt()).isEqualTo(1);
        assertThat(vizinha.path("organization_context").path("seats").path("by_role")).isEmpty();
        assertThat(vizinha.path("organization_context").path("empty_departments")).isEmpty();

        // Ids da EJ de origem não selecionam nada aqui, e não vazam pelo caminho.
        assertThat(summary(outroToken, "?role_id=" + analista).path("total").path("value").asInt())
                .isZero();
        assertThat(summary(outroToken, "?department_id=" + projetos).path("by_department")).isEmpty();
    }

    @Test
    @DisplayName("o resumo não é cacheável: troca de EJ e de permissão mudariam a resposta")
    void shouldNotAllowCaching() {
        assertThat(getWithToken("/v1/members/summary", token).getHeaders().getCacheControl())
                .isEqualTo("private, no-store");
    }

    @Test
    @DisplayName("sem autenticação é 401; sem members:read é 403")
    void shouldRequireAuthorization() {
        assertThat(getWithToken("/v1/members/summary", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken("/v1/members/summary", tokenWithPermissions("Sem Nada", "roles:read"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void assertParity(String query) {
        long naListagem = getJson(token, "/v1/members" + query).path("page").path("total_elements").asLong();
        long noResumo = summary(query).path("total").path("value").asLong();
        assertThat(noResumo).as("resumo e listagem para %s", query.isEmpty() ? "(sem filtro)" : query)
                .isEqualTo(naListagem);
    }

    private JsonNode summary(String query) {
        return summary(token, query);
    }

    private JsonNode summary(String bearer, String query) {
        return getJson(bearer, "/v1/members/summary" + query);
    }

    private static JsonNode seat(JsonNode seats, UUID roleId) {
        for (JsonNode entry : seats.path("by_role"))
            if (roleId.toString().equals(entry.path("role").path("id").asText())) return entry;
        throw new AssertionError("cargo ausente da ocupação: " + roleId);
    }

    private static JsonNode categoryWithNullId(JsonNode distribution) {
        for (JsonNode slice : distribution)
            if (slice.path("key").path("id").isNull()) return slice;
        throw new AssertionError("nenhuma categoria sem vínculo em " + distribution);
    }

    private static long countOf(JsonNode distribution, String id) {
        for (JsonNode slice : distribution)
            if (id.equals(slice.path("key").path("id").asText())) return slice.path("count").asLong();
        return 0;
    }

    private static List<String> ids(JsonNode distribution) {
        return distribution.findValues("key").stream().map(key -> key.path("id").asText()).toList();
    }

    private static List<String> ids(JsonNode list, String field) {
        return list.findValues(field).stream().map(ref -> ref.path("id").asText()).toList();
    }

    private static long sumOf(JsonNode distribution) {
        long sum = 0;
        for (JsonNode slice : distribution) sum += slice.path("count").asLong();
        return sum;
    }

    private static double sumOfShares(JsonNode distribution) {
        double sum = 0;
        for (JsonNode slice : distribution) sum += slice.path("share").asDouble();
        return sum;
    }

    /**
     * Membros por SQL porque o fixture precisa de status, curso, cargo e diretoria escolhidos um a
     * um — e o caminho de escrita da API só cria membro ativo, pelo aceite de convite.
     */
    private void member(String name, MemberStatus status, UUID courseId, UUID roleId, UUID departmentId) {
        jdbc.update("""
                insert into members (id, tenant_id, name, standing, status, course_id, role_id, department_id)
                values (?, ?, ?, 'MEMBER', ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, name, status.name(), courseId, roleId, departmentId);
    }

    private UUID createDepartment(String name) {
        return post("/v1/departments", Map.of("name", name, "description", name), token,
                br.com.puccomp.api.organization.departments.DepartmentResponse.class).getBody().id();
    }

    private UUID createRole(String name, UUID departmentId, Integer maxSeats) {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        body.put("description", name);
        body.put("department_id", departmentId);
        body.put("max_seats", maxSeats);
        return post("/v1/roles", body, token,
                br.com.puccomp.api.organization.roles.RoleResponse.class).getBody().id();
    }

    /** Um membro cujo cargo tem exatamente estas permissões — o dono teria todas. */
    private String tokenWithPermissions(String roleName, String... permissions) {
        UUID roleId = createRole(roleName + " " + UUID.randomUUID().toString().substring(0, 6), null, null);
        put("/v1/roles/" + roleId + "/permissions", Map.of("permissions", List.of(permissions)), token,
                String.class);
        String email = "limitado-" + UUID.randomUUID().toString().substring(0, 8) + "@" + slug + ".dev";
        seeder.seedAccount(tenantId, email, "senha123", Standing.MEMBER, roleId);
        return login(email, "senha123");
    }

    private UUID firstCourse() {
        return get("/v1/courses", token,
                new ParameterizedTypeReference<List<CourseCatalog.CourseOption>>() { })
                .getBody().getFirst().id();
    }

    private JsonNode getJson(String bearer, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        String body = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
