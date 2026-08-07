package br.com.puccomp.api.identity.token;

import br.com.puccomp.api.identity.account.Account;
import br.com.puccomp.api.identity.account.AccountStatus;
import br.com.puccomp.api.shared.reference.Standing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-com-pelo-menos-32-bytes-1234567890";

    private JwtService serviceWith(String secret, Duration ttl) {
        return new JwtService(new JwtProperties(secret, ttl));
    }

    private Account account(UUID id) {
        return Account.builder()
                .id(id)
                .email("dono@ej.dev")
                .passwordHash("hash")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("token gerado decodifica com os mesmos claims do vínculo ativo")
    void shouldRoundTripClaims() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        JwtService jwt = serviceWith(SECRET, Duration.ofHours(1));

        Jwt decoded = jwt.decode(jwt.generateAccessToken(account(id), tenant, member, Standing.OWNER));

        assertThat(decoded.getSubject()).isEqualTo(id.toString());
        assertThat(decoded.getClaimAsString("tenant_id")).isEqualTo(tenant.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("dono@ej.dev");
        assertThat(decoded.getClaimAsString("standing")).isEqualTo("OWNER");
        assertThat(decoded.getClaimAsString("member_id")).isEqualTo(member.toString());
    }

    @Test
    @DisplayName("token expirado é rejeitado (além da tolerância de clock skew)")
    void shouldRejectExpiredToken() {
        Clock twoHoursAgo = Clock.fixed(Clock.systemUTC().instant().minusSeconds(7200), ZoneOffset.UTC);
        JwtService jwt = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)), twoHoursAgo);
        String token = jwt.generateAccessToken(account(UUID.randomUUID()), UUID.randomUUID(),
                UUID.randomUUID(), Standing.MEMBER);

        assertThatThrownBy(() -> jwt.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token assinado com outro secret é rejeitado")
    void shouldRejectTamperedSignature() {
        String token = serviceWith(SECRET, Duration.ofHours(1))
                .generateAccessToken(account(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(),
                        Standing.MEMBER);
        JwtService other = serviceWith("outro-secret-com-pelo-menos-32-bytes-0987654321", Duration.ofHours(1));

        assertThatThrownBy(() -> other.decode(token)).isInstanceOf(JwtException.class);
    }
}
