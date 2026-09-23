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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class AlumniAccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("aposentar vale na hora: o mesmo token do membro passa a ter acesso somente leitura")
    void shouldGiveRetiredMemberLiveReadOnlyAccess() {
        // slug e e-mail são únicos no banco inteiro, e o CandidateApplicationIntegrationTest
        // também semeia uma "EJ Alumni": fixar os dois faz a segunda classe da rodada quebrar.
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = seeder.seedTenant("EJ Alumni", "ej-alumni-" + sufixo);
        seeder.seedAccount(tenant, "dono-" + sufixo + "@alumni.dev", "senha123", Standing.OWNER);
        UUID veteranoId = seeder.seedAccount(tenant, "veterano-" + sufixo + "@alumni.dev", "senha123", Standing.MEMBER);

        String owner = login("dono-" + sufixo + "@alumni.dev", "senha123");
        String veterano = login("veterano-" + sufixo + "@alumni.dev", "senha123");

        assertThat(getWithToken("/v1/members", veterano).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(status(veteranoId, "ALUMNUS", owner).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(getWithToken("/v1/members", veterano).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(veteranoId, "ACTIVE", veterano).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("reativar um alumni restaura o acesso de membro comum")
    void shouldRestoreAccessWhenReactivated() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = seeder.seedTenant("EJ Retorno", "ej-retorno-" + sufixo);
        seeder.seedAccount(tenant, "dono-" + sufixo + "@retorno.dev", "senha123", Standing.OWNER);
        UUID veteranoId = seeder.seedAccount(tenant, "veterano-" + sufixo + "@retorno.dev", "senha123", Standing.MEMBER);
        String owner = login("dono-" + sufixo + "@retorno.dev", "senha123");

        status(veteranoId, "ALUMNUS", owner);
        assertThat(status(veteranoId, "ACTIVE", owner).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String veterano = login("veterano-" + sufixo + "@retorno.dev", "senha123");
        assertThat(getWithToken("/v1/members", veterano).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> status(UUID memberId, String value, String bearerToken) {
        return put("/v1/members/" + memberId + "/status", Map.of("value", value), bearerToken,
                String.class);
    }

    private ResponseEntity<String> post(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }
}
