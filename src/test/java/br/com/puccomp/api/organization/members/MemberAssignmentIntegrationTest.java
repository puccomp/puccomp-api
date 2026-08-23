package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.departments.DepartmentRequest;
import br.com.puccomp.api.organization.departments.DepartmentResponse;
import br.com.puccomp.api.organization.roles.RoleRequest;
import br.com.puccomp.api.organization.roles.RoleResponse;
import br.com.puccomp.api.organization.roles.RoleUpdateRequest;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class MemberAssignmentIntegrationTest extends AbstractIntegrationTest {

    private static final String MEMBERS = "/v1/members";
    private static final String ROLES = "/v1/roles";
    private static final String DEPARTMENTS = "/v1/departments";

    @Autowired
    private TestSeeder seeder;

    private record MemberPage(List<MemberResponse> content) {
    }

    @Test
    @DisplayName("cargo com diretoria impõe a sua ao membro, mesmo sem department_id no corpo")
    void shouldInheritDepartmentFromRole() {
        UUID tenant = seeder.seedTenant("EJ Herda Diretoria", "ej-herda-dir");
        String owner = ownerOf(tenant, "dono-herda@ej.dev");
        UUID membro = seeder.seedAccount(tenant, "membro-herda@ej.dev", "senha123", Standing.MEMBER);
        UUID marketing = createDepartment(owner, "Marketing").id();
        UUID diretorMarketing = createRole(owner, "Diretor de Marketing", marketing).id();

        ResponseEntity<MemberResponse> res = put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(diretorMarketing, null), owner, MemberResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().role()).isEqualTo("Diretor de Marketing");
        assertThat(res.getBody().department()).isEqualTo("Marketing");
    }

    @Test
    @DisplayName("diretoria divergente da diretoria do cargo é recusada")
    void shouldRejectDepartmentThatDivergesFromRole() {
        UUID tenant = seeder.seedTenant("EJ Diverge", "ej-diverge");
        String owner = ownerOf(tenant, "dono-diverge@ej.dev");
        UUID membro = seeder.seedAccount(tenant, "membro-diverge@ej.dev", "senha123", Standing.MEMBER);
        UUID marketing = createDepartment(owner, "Marketing").id();
        UUID projetos = createDepartment(owner, "Projetos").id();
        UUID diretorMarketing = createRole(owner, "Diretor de Marketing", marketing).id();

        ResponseEntity<ErrorResponse> res = put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(diretorMarketing, projetos), owner, ErrorResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("cargo genérico deixa a diretoria do membro livre")
    void shouldAllowFreeDepartmentForGenericRole() {
        UUID tenant = seeder.seedTenant("EJ Cargo Livre", "ej-cargo-livre");
        String owner = ownerOf(tenant, "dono-livre@ej.dev");
        UUID membro = seeder.seedAccount(tenant, "membro-livre@ej.dev", "senha123", Standing.MEMBER);
        UUID tecnologia = createDepartment(owner, "Tecnologia").id();
        UUID trainee = createRole(owner, "Trainee", null).id();

        ResponseEntity<MemberResponse> res = put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(trainee, tecnologia), owner, MemberResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().role()).isEqualTo("Trainee");
        assertThat(res.getBody().department()).isEqualTo("Tecnologia");
    }

    @Test
    @DisplayName("corpo sem cargo e sem diretoria limpa os dois vínculos do membro")
    void shouldClearRoleAndDepartment() {
        UUID tenant = seeder.seedTenant("EJ Limpa Vinculo", "ej-limpa-vinculo");
        String owner = ownerOf(tenant, "dono-limpa@ej.dev");
        UUID membro = seeder.seedAccount(tenant, "membro-limpa@ej.dev", "senha123", Standing.MEMBER);
        UUID comercial = createDepartment(owner, "Comercial").id();
        UUID diretorComercial = createRole(owner, "Diretor Comercial", comercial).id();
        put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(diretorComercial, null), owner, MemberResponse.class);

        ResponseEntity<MemberResponse> res = put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(null, null), owner, MemberResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().role()).isNull();
        assertThat(res.getBody().department()).isNull();
    }

    @Test
    @DisplayName("cargo inativo não pode ser atribuído")
    void shouldRejectInactiveRole() {
        UUID tenant = seeder.seedTenant("EJ Cargo Inativo", "ej-cargo-inativo");
        String owner = ownerOf(tenant, "dono-inativo@ej.dev");
        UUID membro = seeder.seedAccount(tenant, "membro-inativo@ej.dev", "senha123", Standing.MEMBER);
        UUID trainee = createRole(owner, "Trainee", null).id();
        put(ROLES + "/" + trainee, new RoleUpdateRequest("Trainee", "Membro em formação", null, null, false),
                owner, RoleResponse.class);

        ResponseEntity<ErrorResponse> res = put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(trainee, null), owner, ErrorResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("membro provisionado para cargo com diretoria já nasce com ela")
    void shouldInheritDepartmentWhenMemberIsProvisioned() {
        UUID tenant = seeder.seedTenant("EJ Convite Diretoria", "ej-convite-dir");
        String owner = ownerOf(tenant, "dono-convite-dir@ej.dev");
        UUID projetos = createDepartment(owner, "Projetos").id();
        UUID diretorProjetos = createRole(owner, "Diretor de Projetos", projetos).id();

        UUID convidado = seeder.seedAccount(tenant, "convidado@ej.dev", "senha123",
                Standing.MEMBER, diretorProjetos);

        ResponseEntity<MemberResponse> res = get(MEMBERS + "/" + convidado, owner, MemberResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().department()).isEqualTo("Projetos");
    }

    @Test
    @DisplayName("listagem de membros filtra por diretoria e devolve página vazia para id desconhecido")
    void shouldFilterMembersByDepartment() {
        UUID tenant = seeder.seedTenant("EJ Filtro Membros", "ej-filtro-membros");
        String owner = ownerOf(tenant, "dono-filtro-membros@ej.dev");
        UUID membro = seeder.seedAccount(tenant, "membro-filtro@ej.dev", "senha123", Standing.MEMBER);
        UUID tecnologia = createDepartment(owner, "Tecnologia").id();
        UUID diretorTecnologia = createRole(owner, "Diretor de Tecnologia", tecnologia).id();
        put(MEMBERS + "/" + membro + "/assignment",
                new MemberAssignmentRequest(diretorTecnologia, null), owner, MemberResponse.class);

        assertThat(names(owner, "?departmentId=" + tecnologia)).containsExactly("membro-filtro@ej.dev");
        assertThat(names(owner, "?departmentId=" + UUID.randomUUID())).isEmpty();
        assertThat(names(owner, "")).hasSize(2);
    }

    private String ownerOf(UUID tenantId, String email) {
        seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private DepartmentResponse createDepartment(String token, String name) {
        ResponseEntity<DepartmentResponse> res = post(DEPARTMENTS,
                new DepartmentRequest(name, "Diretoria " + name), token, DepartmentResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private RoleResponse createRole(String token, String name, UUID departmentId) {
        ResponseEntity<RoleResponse> res = post(ROLES,
                new RoleRequest(name, "Cargo " + name, departmentId, null), token, RoleResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private List<String> names(String token, String query) {
        ResponseEntity<MemberPage> res = get(MEMBERS + query, token, MemberPage.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().content().stream().map(MemberResponse::name).toList();
    }
}
