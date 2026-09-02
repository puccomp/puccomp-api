package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.organization.departments.DepartmentRequest;
import br.com.puccomp.api.organization.departments.DepartmentResponse;
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
class RoleIntegrationTest extends AbstractIntegrationTest {

    private static final String ROLES = "/v1/roles";
    private static final String DEPARTMENTS = "/v1/departments";

    @Autowired
    private TestSeeder seeder;

    private record RolePage(List<RoleResponse> content) {
    }

    @Test
    @DisplayName("cargo nasce vinculado à diretoria informada e devolve id e nome dela")
    void shouldCreateRoleAttachedToDepartment() {
        String owner = ownerOf("EJ Cargo Com Diretoria", "ej-cargo-com-dir", "dono-cargo-dir@ej.dev");
        UUID marketing = createDepartment(owner, "Marketing").id();

        RoleResponse created = createRole(owner, "Diretor de Marketing", marketing);

        assertThat(created.department()).isEqualTo(new NamedRef(marketing, "Marketing"));
    }

    @Test
    @DisplayName("cargo genérico é criado sem diretoria")
    void shouldCreateRoleWithoutDepartment() {
        String owner = ownerOf("EJ Cargo Generico", "ej-cargo-generico", "dono-cargo-generico@ej.dev");

        RoleResponse created = createRole(owner, "Trainee", null);

        assertThat(created.department()).isNull();
    }

    @Test
    @DisplayName("cargo com diretoria inexistente é recusado")
    void shouldRejectRoleWithUnknownDepartment() {
        String owner = ownerOf("EJ Diretoria Fantasma", "ej-dir-fantasma", "dono-dir-fantasma@ej.dev");

        ResponseEntity<ErrorResponse> res = post(ROLES,
                new RoleRequest("Diretor", "Coordena", UUID.randomUUID(), 1), owner, ErrorResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("diretoria de outra EJ não pode ser vinculada a um cargo")
    void shouldRejectDepartmentFromAnotherTenant() {
        String alfa = ownerOf("EJ Alfa Cargos", "ej-alfa-cargos", "dono-alfa-cargos@ej.dev");
        String beta = ownerOf("EJ Beta Cargos", "ej-beta-cargos", "dono-beta-cargos@ej.dev");
        UUID betaDepartment = createDepartment(beta, "Tecnologia").id();

        ResponseEntity<ErrorResponse> res = post(ROLES,
                new RoleRequest("Diretor de Tecnologia", "Coordena", betaDepartment, 1), alfa, ErrorResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("atualização com department_id nulo desvincula o cargo da diretoria")
    void shouldDetachDepartmentOnUpdate() {
        String owner = ownerOf("EJ Desvincula", "ej-desvincula", "dono-desvincula@ej.dev");
        UUID projetos = createDepartment(owner, "Projetos").id();
        RoleResponse created = createRole(owner, "Diretor de Projetos", projetos);

        ResponseEntity<RoleResponse> res = put(ROLES + "/" + created.id(),
                new RoleUpdateRequest("Conselheiro", "Aconselha a diretoria executiva", null, null, true),
                owner, RoleResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoleResponse updated = res.getBody();
        assertThat(updated.department()).isNull();
        assertThat(updated.name()).isEqualTo("Conselheiro");
        assertThat(updated.maxSeats()).isNull();
        assertThat(updated.active()).isTrue();
    }

    @Test
    @DisplayName("atualização recusa nome já usado por outro cargo, mas aceita manter o próprio")
    void shouldRejectNameAlreadyTakenByAnotherRole() {
        String owner = ownerOf("EJ Nome Repetido", "ej-nome-repetido", "dono-nome-repetido@ej.dev");
        createRole(owner, "Presidente", null);
        RoleResponse trainee = createRole(owner, "Trainee", null);

        ResponseEntity<ErrorResponse> conflito = put(ROLES + "/" + trainee.id(),
                new RoleUpdateRequest("Presidente", "Lidera a EJ", null, null, true), owner, ErrorResponse.class);
        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<RoleResponse> mesmoNome = put(ROLES + "/" + trainee.id(),
                new RoleUpdateRequest("Trainee", "Membro em formação inicial", null, 5, true),
                owner, RoleResponse.class);
        assertThat(mesmoNome.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mesmoNome.getBody().maxSeats()).isEqualTo(5);
    }

    @Test
    @DisplayName("listagem filtra por diretoria e devolve página vazia para id desconhecido")
    void shouldFilterRolesByDepartment() {
        String owner = ownerOf("EJ Filtro Cargos", "ej-filtro-cargos", "dono-filtro-cargos@ej.dev");
        UUID comercial = createDepartment(owner, "Comercial").id();
        createRole(owner, "Diretor Comercial", comercial);
        createRole(owner, "Trainee", null);

        assertThat(names(owner, "")).containsExactlyInAnyOrder("Diretor Comercial", "Trainee");
        assertThat(names(owner, "?departmentId=" + comercial)).containsExactly("Diretor Comercial");
        assertThat(names(owner, "?departmentId=" + UUID.randomUUID())).isEmpty();
    }

    private String ownerOf(String tenantName, String slug, String email) {
        UUID tenant = seeder.seedTenant(tenantName, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
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
        ResponseEntity<RolePage> res = get(ROLES + query, token, RolePage.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().content().stream().map(RoleResponse::name).toList();
    }
}
