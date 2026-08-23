package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class AlumniAccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("aposentar vale na hora: o mesmo token do membro passa a ter acesso somente leitura")
    void shouldGiveRetiredMemberLiveReadOnlyAccess() {
        UUID tenant = seeder.seedTenant("EJ Alumni", "ej-alumni");
        seeder.seedAccount(tenant, "dono@alumni.dev", "senha123", Standing.OWNER);
        UUID veteranoId = seeder.seedAccount(tenant, "veterano@alumni.dev", "senha123", Standing.MEMBER);

        String owner = login("dono@alumni.dev", "senha123");
        String veterano = login("veterano@alumni.dev", "senha123");

        assertThat(getWithToken("/v1/members", veterano).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(post("/v1/members/" + veteranoId + "/retire", owner).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(getWithToken("/v1/members", veterano).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/v1/members/" + veteranoId + "/reactivate", veterano).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("reativar um alumni restaura o acesso de membro comum")
    void shouldRestoreAccessWhenReactivated() {
        UUID tenant = seeder.seedTenant("EJ Retorno", "ej-retorno");
        seeder.seedAccount(tenant, "dono@retorno.dev", "senha123", Standing.OWNER);
        UUID veteranoId = seeder.seedAccount(tenant, "veterano@retorno.dev", "senha123", Standing.MEMBER);
        String owner = login("dono@retorno.dev", "senha123");

        post("/v1/members/" + veteranoId + "/retire", owner);
        assertThat(post("/v1/members/" + veteranoId + "/reactivate", owner).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String veterano = login("veterano@retorno.dev", "senha123");
        assertThat(getWithToken("/v1/members", veterano).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> post(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }
}
