package br.com.puccomp.api.organization.departments;

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
class DepartmentIntegrationTest extends AbstractIntegrationTest {

    private static final String DEPARTMENTS = "/v1/departments";

    @Autowired
    private TestSeeder seeder;

    private record DepartmentPage(List<DepartmentResponse> content) {
    }

    @Test
    @DisplayName("diretoria criada nasce ativa e aparece na busca por id e na listagem")
    void shouldCreateAndReadDepartment() {
        String owner = ownerOf("EJ Diretorias", "ej-diretorias", "dono-diretorias@ej.dev");

        DepartmentResponse created = createDepartment(owner, "Marketing", "Comunicação, marca e captação");
        assertThat(created.active()).isTrue();

        ResponseEntity<DepartmentResponse> byId =
                get(DEPARTMENTS + "/" + created.id(), owner, DepartmentResponse.class);

        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byId.getBody().name()).isEqualTo("Marketing");
        assertThat(byId.getBody().description()).isEqualTo("Comunicação, marca e captação");
        assertThat(names(owner)).containsExactly("Marketing");
    }

    @Test
    @DisplayName("nome de diretoria não se repete na mesma EJ, mesmo com outra caixa")
    void shouldRejectDuplicateDepartmentName() {
        String owner = ownerOf("EJ Diretoria Duplicada", "ej-dir-duplicada", "dono-dir-duplicada@ej.dev");
        createDepartment(owner, "Comercial", "Prospecção e fechamento de projetos");

        ResponseEntity<ErrorResponse> res = post(DEPARTMENTS,
                new DepartmentRequest("comercial", "Outra descrição"), owner, ErrorResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("diretorias são isoladas entre EJs")
    void shouldIsolateDepartmentsBetweenTenants() {
        String alfa = ownerOf("EJ Alfa Diretorias", "ej-alfa-dir", "dono-alfa-dir@ej.dev");
        String beta = ownerOf("EJ Beta Diretorias", "ej-beta-dir", "dono-beta-dir@ej.dev");

        UUID betaDepartmentId = createDepartment(beta, "Projetos", "Execução e entrega").id();

        assertThat(names(alfa)).isEmpty();
        assertThat(get(DEPARTMENTS + "/" + betaDepartmentId, alfa, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String ownerOf(String tenantName, String slug, String email) {
        UUID tenant = seeder.seedTenant(tenantName, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private DepartmentResponse createDepartment(String token, String name, String description) {
        ResponseEntity<DepartmentResponse> res = post(DEPARTMENTS,
                new DepartmentRequest(name, description), token, DepartmentResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private List<String> names(String token) {
        ResponseEntity<DepartmentPage> res = get(DEPARTMENTS, token, DepartmentPage.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().content().stream().map(DepartmentResponse::name).toList();
    }
}
