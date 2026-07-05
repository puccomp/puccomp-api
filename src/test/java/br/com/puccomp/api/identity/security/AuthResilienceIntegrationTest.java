package br.com.puccomp.api.identity.security;

import br.com.puccomp.api.identity.token.JwtProperties;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResilienceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("rota pública é acessível sem token")
    void shouldAllowPublicRouteWithoutToken() {
        ResponseEntity<String> res = rest.getForEntity("/v1/courses", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("rota protegida sem token retorna 401 limpo, sem stacktrace")
    void shouldReturnClean401WhenNoToken() {
        ResponseEntity<String> res = rest.getForEntity("/v1/members", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).contains("trace_id");
        assertNoLeak(res.getBody());
    }

    @Test
    @DisplayName("token lixo retorna 401 e não quebra")
    void shouldReturn401ForGarbageToken() {
        ResponseEntity<String> res = getWithToken("/v1/members", "isto-nao-e-um-jwt");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNoLeak(res.getBody());
    }

    @Test
    @DisplayName("JWT válido sem a claim standing retorna 401, não 500")
    void shouldReturn401NotServerErrorWhenJwtMissingStanding() {
        ResponseEntity<String> res = getWithToken("/v1/members", mintTokenWithoutStanding());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNoLeak(res.getBody());
    }

    private static void assertNoLeak(String body) {
        assertThat(body)
                .doesNotContain("\"trace\"")
                .doesNotContain("Exception")
                .doesNotContain("at br.com.puccomp")
                .doesNotContain("Internal Server Error");
    }

    private String mintTokenWithoutStanding() {
        byte[] secret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("tenant_id", UUID.randomUUID().toString())
                .claim("email", "antigo@ejcomp.dev")
                .claim("member_id", UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
