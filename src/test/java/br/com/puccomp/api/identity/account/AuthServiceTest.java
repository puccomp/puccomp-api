package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.identity.tenant.OrganizationView;
import br.com.puccomp.api.identity.tenant.Tenant;
import br.com.puccomp.api.identity.tenant.TenantRepository;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberDirectory.Membership;
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
import static org.mockito.Mockito.verifyNoInteractions;

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
    @Mock
    private TenantRepository tenants;
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
    @DisplayName("conta em várias EJs sem organization_id recebe id, nome e slug de cada vínculo")
    void shouldRequireTenantWhenMultipleMemberships() {
        Account acc = account(AccountStatus.ACTIVE);
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        Membership comp = membership();
        Membership outra = membership();
        when(memberDirectory.findMembershipsByAccount(acc.getId())).thenReturn(List.of(comp, outra));
        when(tenants.findAllById(List.of(comp.tenantId(), outra.tenantId()))).thenReturn(List.of(
                Tenant.builder().id(comp.tenantId()).name("Comp").slug("ej-comp").build(),
                Tenant.builder().id(outra.tenantId()).name("Outra EJ").slug("outra-ej").build()));

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "senha", null)))
                .isInstanceOfSatisfying(OrganizationChoiceRequiredException.class, ex ->
                        assertThat(ex.getOrganizations()).containsExactlyInAnyOrder(
                                new OrganizationView(comp.tenantId(), "Comp", "ej-comp"),
                                new OrganizationView(outra.tenantId(), "Outra EJ", "outra-ej")));
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("repetir login com a EJ escolhida emite o token daquele vínculo")
    void shouldReturnTokenForChosenOrganization() {
        Account acc = account(AccountStatus.ACTIVE);
        Membership first = membership();
        Membership chosen = membership();
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(memberDirectory.findMembershipsByAccount(acc.getId())).thenReturn(List.of(first, chosen));
        when(jwtService.generateAccessToken(acc, chosen.tenantId(), chosen.memberId(), chosen.standing()))
                .thenReturn("jwt-chosen");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(28800L);

        LoginResponse response = service.login(new LoginRequest("dono@ej.dev", "senha", chosen.tenantId()));

        assertThat(response.accessToken()).isEqualTo("jwt-chosen");
        verifyNoInteractions(tenants);
    }

    @Test
    @DisplayName("organization_id sem vínculo é rejeitado sem revelar outras EJs")
    void shouldRejectOrganizationWithoutMembership() {
        Account acc = account(AccountStatus.ACTIVE);
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(acc));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(memberDirectory.findMembershipsByAccount(acc.getId()))
                .thenReturn(List.of(membership(), membership()));

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "senha", UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciais inválidas");
        verifyNoInteractions(tenants, jwtService);
    }

    @Test
    @DisplayName("senha incorreta é rejeitada")
    void shouldRejectWrongPassword() {
        when(accounts.findByEmailIgnoreCase("dono@ej.dev")).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("dono@ej.dev", "errada", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciais inválidas");
        verifyNoInteractions(memberDirectory, tenants, jwtService);
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
