package br.com.puccomp.api.identity.token;

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
class PatIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("escopo fora do catálogo é recusado na criação, com o código errado na mensagem")
    void shouldRejectUnknownScopes() {
        String owner = donoDe("EJ PAT escopo", "ej-pat-escopo", "dono-escopo@ej.dev");

        ResponseEntity<String> res = criarPat(owner, List.of("members:read", "members:reed"), String.class);

        // Sem esta recusa o token nasceria válido e sem poder nenhum: o filtro intersecta escopo com
        // permissão efetiva, e "members:reed" simplesmente não sobrevive à interseção.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("members:reed").doesNotContain("members:read\"");
    }

    @Test
    @DisplayName("o catálogo de escopos é legível por quem cria o token, não só por quem administra permissões")
    void shouldPublishScopesToAnyAuthenticatedMember() {
        UUID tenant = seeder.seedTenant("EJ PAT catálogo", "ej-pat-catalogo");
        seeder.seedAccount(tenant, "membro-catalogo@ej.dev", "senha123", Standing.MEMBER);

        String membro = login("membro-catalogo@ej.dev", "senha123");

        // O membro comum não tem permissions:manage, e é justamente ele quem monta o PAT.
        assertThat(getWithToken("/v1/permissions", membro).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> res = getWithToken("/v1/auth/pat/scopes", membro);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("members:read").contains("financial:write");
    }

    @Test
    @DisplayName("uso repetido do token não regrava last_used_at a cada chamada")
    void shouldThrottleLastUsedWrites() {
        String owner = donoDe("EJ PAT uso", "ej-pat-uso", "dono-uso@ej.dev");
        String pat = criarPat(owner, null, Map.class).getBody().get("token").toString();

        assertThat(getWithToken("/v1/members", pat).getStatusCode()).isEqualTo(HttpStatus.OK);
        Object primeiro = lastUsedAt(owner);

        assertThat(getWithToken("/v1/members", pat).getStatusCode()).isEqualTo(HttpStatus.OK);
        Object segundo = lastUsedAt(owner);

        // Um agente percorre este caminho muitas vezes por minuto; gravar sempre seria um UPDATE
        // por chamada para uma informação que só precisa dizer se o token ainda circula.
        assertThat(primeiro).isNotNull();
        assertThat(segundo).isEqualTo(primeiro);
    }

    private String donoDe(String nome, String slug, String email) {
        UUID tenant = seeder.seedTenant(nome, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private <T> ResponseEntity<T> criarPat(String ownerToken, List<String> scopes, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("name", "automacao");
        body.put("scopes", scopes);
        return rest.exchange("/v1/auth/pat", HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private Object lastUsedAt(String ownerToken) {
        ResponseEntity<List<Map<String, Object>>> res = get("/v1/auth/pat", ownerToken,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(1);
        return res.getBody().get(0).get("last_used_at");
    }
}
