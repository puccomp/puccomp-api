package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberDirectory.Membership;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.UnauthorizedException;
import br.com.puccomp.api.shared.reference.Standing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private MemberDirectory memberDirectory;
    @InjectMocks
    private AuthService service;

    private Account account(AccountStatus status) {
        return Account.builder()
                .id(UUID.randomUUID())
                .email("dono@ej.dev")
                .passwordHash("hash")
                .status(status)
                .build();
    }

    private Membership membership() {
        return new Membership(UUID.randomUUID(), UUID.randomUUID(), Standing.OWNER);
    }

    @Test
    @DisplayName("login com credenciais válidas e um único vínculo retorna access token")
    void shouldReturnTokenOnValidCredentials() {
        Account acc = account(AccountStatus.ACTIVE);
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(memberDirectory.findMembershipsByAccount(acc.getId())).thenReturn(List.of(membership()));
        when(jwtService.generateAccessToken(eq(acc), any(), any(), any())).thenReturn("jwt-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(28800L);

        LoginResponse response = service.login(new LoginRequest("dono@ej.dev", "senha", null));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(28800L);
    }

    @Test
    @DisplayName("conta sem vínculo com nenhuma EJ não autentica")
    void shouldRejectAccountWithoutMembership() {
        Account acc = account(AccountStatus.ACTIVE);
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(memberDirectory.findMembershipsByAccount(acc.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "senha", null)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("conta em várias EJs sem tenantId exige desambiguação (409)")
    void shouldRequireTenantWhenMultipleMemberships() {
        Account acc = account(AccountStatus.ACTIVE);
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(memberDirectory.findMembershipsByAccount(acc.getId()))
                .thenReturn(List.of(membership(), membership()));

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "senha", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("senha incorreta é rejeitada")
    void shouldRejectWrongPassword() {
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "errada", null)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("e-mail inexistente é rejeitado")
    void shouldRejectUnknownEmail() {
        when(accounts.findByEmailIgnoreCase("ninguem@ej.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@ej.dev", "x", null)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("conta desativada não autentica")
    void shouldRejectDisabledAccount() {
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(account(AccountStatus.DISABLED)));

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "senha", null)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
