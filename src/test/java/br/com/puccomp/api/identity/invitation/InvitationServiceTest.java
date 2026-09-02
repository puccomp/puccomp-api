package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.account.*;
import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.identity.tenant.TenantRepository;
import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberDirectory.Membership;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.reference.Standing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository repository;
    @Mock private AccountRepository accounts;
    @Mock private TenantRepository tenants;
    @Mock private MemberProvisioning memberProvisioning;
    @Mock private MemberDirectory memberDirectory;
    @Mock private CourseCatalog courseCatalog;
    @Mock private InvitationAcceptor acceptor;
    @Mock private JwtService jwtService;
    @Mock private Mailer mailer;
    @Mock private OnboardingProperties properties;
    @InjectMocks private InvitationService service;

    private AuthPrincipal admin() {
        return new AuthPrincipal(UUID.randomUUID(), "dono@ej.dev", UUID.randomUUID(), null, Standing.OWNER, null);
    }

    private AuthPrincipal member() {
        return new AuthPrincipal(UUID.randomUUID(), "member@ej.dev", UUID.randomUUID(), UUID.randomUUID(),
                Standing.MEMBER, null);
    }

    private Invitation invitation(Instant expiresAt) {
        return Invitation.builder()
                .tenantId(UUID.randomUUID())
                .email("novato@ej.dev")
                .standing(Standing.MEMBER)
                .tokenHash("hash")
                .tokenPrefix("inv_abc")
                .expiresAt(expiresAt)
                .createdByAccountId(UUID.randomUUID())
                .build();
    }

    @Test
    @DisplayName("criar convite gera token inv_, persiste e dispara email")
    void shouldCreateInvitation() {
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.empty());
        when(properties.invitationTtl()).thenReturn(Duration.ofHours(72));
        when(properties.acceptUrlBase()).thenReturn("http://localhost/aceitar");

        InvitationResponse response = service.create(admin(),
                new CreateInvitationRequest("novato@ej.dev", Standing.MEMBER, null));

        assertThat(response.email()).isEqualTo("novato@ej.dev");
        assertThat(response.tokenPrefix()).startsWith("inv_");

        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).hasSize(64);
        assertThat(sentInvitation().acceptUrl()).startsWith("http://localhost/aceitar");
    }

    @Test
    @DisplayName("convite para quem já é membro desta EJ é rejeitado (409)")
    void shouldRejectIfAlreadyMemberOfTenant() {
        AuthPrincipal admin = admin();
        Account existing = Account.builder().id(UUID.randomUUID()).email("novato@ej.dev").build();
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.of(existing));
        when(memberDirectory.findMembership(existing.getId(), admin.tenantId()))
                .thenReturn(Optional.of(new Membership(UUID.randomUUID(), admin.tenantId(), Standing.MEMBER)));

        assertThatThrownBy(() -> service.create(admin,
                new CreateInvitationRequest("novato@ej.dev", Standing.MEMBER, null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("convite para conta existente ainda não vinculada a esta EJ é permitido (multi-EJ)")
    void shouldAllowInvitingExistingAccountToNewTenant() {
        AuthPrincipal admin = admin();
        Account existing = Account.builder().id(UUID.randomUUID()).email("veterano@ej.dev").build();
        when(accounts.findByEmailIgnoreCase("veterano@ej.dev")).thenReturn(Optional.of(existing));
        when(memberDirectory.findMembership(existing.getId(), admin.tenantId())).thenReturn(Optional.empty());
        when(properties.invitationTtl()).thenReturn(Duration.ofHours(72));
        when(properties.acceptUrlBase()).thenReturn("http://localhost/aceitar");

        InvitationResponse response = service.create(admin,
                new CreateInvitationRequest("veterano@ej.dev", Standing.MEMBER, null));

        assertThat(response.email()).isEqualTo("veterano@ej.dev");
        verify(repository).save(any());
    }

    @Test
    @DisplayName("convite com cargo inexistente é rejeitado (404)")
    void shouldRejectUnknownCargo() {
        UUID cargo = UUID.randomUUID();
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.empty());
        when(memberProvisioning.roleExists(cargo)).thenReturn(false);

        assertThatThrownBy(() -> service.create(admin(),
                new CreateInvitationRequest("novato@ej.dev", Standing.MEMBER, cargo)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("standing omitido (null) usa MEMBER por padrão")
    void shouldDefaultStandingToMember() {
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.empty());
        when(properties.invitationTtl()).thenReturn(Duration.ofHours(72));
        when(properties.acceptUrlBase()).thenReturn("http://localhost/aceitar");

        service.create(admin(), new CreateInvitationRequest("novato@ej.dev", null, null));

        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStanding()).isEqualTo(Standing.MEMBER);
    }

    @Test
    @DisplayName("MEMBER não pode convidar com standing OWNER: 403")
    void shouldRejectMemberInvitingOwnerStanding() {
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(member(),
                new CreateInvitationRequest("novato@ej.dev", Standing.OWNER, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("OWNER pode convidar com standing OWNER")
    void shouldAllowOwnerInvitingOwnerStanding() {
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.empty());
        when(properties.invitationTtl()).thenReturn(Duration.ofHours(72));
        when(properties.acceptUrlBase()).thenReturn("http://localhost/aceitar");

        InvitationResponse response = service.create(admin(),
                new CreateInvitationRequest("novato@ej.dev", Standing.OWNER, null));

        assertThat(response.standing()).isEqualTo(Standing.OWNER);
        verify(repository).save(any());
    }

    @Test
    @DisplayName("aceite válido delega a escrita e devolve access token")
    void shouldAcceptAndReturnToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(invitation(Instant.now().plusSeconds(3600))));
        Account account = Account.builder().id(UUID.randomUUID()).email("novato@ej.dev")
                .status(AccountStatus.ACTIVE).build();
        when(acceptor.provision(any(), any()))
                .thenReturn(new InvitationAcceptor.Provisioned(account, UUID.randomUUID(), Standing.MEMBER));
        when(jwtService.generateAccessToken(eq(account), any(), any(), any())).thenReturn("jwt-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(28800L);

        LoginResponse response = service.accept(
                new AcceptInvitationRequest("inv_token", "senha123", "Novato", UUID.randomUUID()));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        verify(acceptor).provision(any(), any());
    }

    @Test
    @DisplayName("aceite com token expirado é rejeitado, sem tocar na escrita")
    void shouldRejectExpiredToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(invitation(Instant.now().minusSeconds(3600))));

        assertThatThrownBy(() -> service.accept(
                new AcceptInvitationRequest("inv_token", "senha123", "Novato", UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(acceptor, never()).provision(any(), any());
    }

    /** O corpo do email deixou de ser problema daqui: o service só entrega dados. */
    private EmailMessage.Invitation sentInvitation() {
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailer).send(sent.capture());
        assertThat(sent.getValue()).isInstanceOf(EmailMessage.Invitation.class);
        return (EmailMessage.Invitation) sent.getValue();
    }

    private Invitation outstanding(UUID tenantId) {
        return Invitation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .email("novato@ej.dev")
                .standing(Standing.MEMBER)
                .tokenHash("old-hash")
                .tokenPrefix("inv_old")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdByAccountId(UUID.randomUUID())
                .build();
    }

    @Test
    @DisplayName("listar em aberto deriva status PENDING para válidos e EXPIRED para vencidos")
    void shouldListOutstandingWithDerivedStatus() {
        UUID tenant = UUID.randomUUID();
        Invitation valido = invitation(Instant.now().plusSeconds(3600));
        Invitation vencido = invitation(Instant.now().minusSeconds(3600));
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNull(tenant, pageable))
                .thenReturn(new PageImpl<>(List.of(valido, vencido)));

        var page = service.listOutstanding(tenant, pageable);

        assertThat(page.getContent()).extracting(InvitationResponse::status)
                .containsExactly(InvitationStatus.PENDING, InvitationStatus.EXPIRED);
    }

    @Test
    @DisplayName("reenvio rotaciona o token, estende a validade e dispara novo email")
    void shouldResendRotatingTokenAndEmail() {
        UUID tenant = UUID.randomUUID();
        Invitation invitation = outstanding(tenant);
        Instant validadeAntiga = invitation.getExpiresAt();
        when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(properties.invitationTtl()).thenReturn(Duration.ofHours(72));
        when(properties.acceptUrlBase()).thenReturn("http://localhost/aceitar");

        InvitationResponse response = service.resend(tenant, invitation.getId());

        assertThat(invitation.getTokenHash()).isNotEqualTo("old-hash").hasSize(64);
        assertThat(invitation.getTokenPrefix()).startsWith("inv_");
        assertThat(invitation.getExpiresAt()).isAfter(validadeAntiga);
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(sentInvitation().acceptUrl()).startsWith("http://localhost/aceitar");
    }

    @Test
    @DisplayName("reenvio de convite já aceito é rejeitado (409), sem novo email")
    void shouldRejectResendOfAcceptedInvitation() {
        UUID tenant = UUID.randomUUID();
        Invitation invitation = outstanding(tenant);
        invitation.markAccepted(Instant.now());
        when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.resend(tenant, invitation.getId()))
                .isInstanceOf(ConflictException.class);
        verify(mailer, never()).send(any());
    }

    @Test
    @DisplayName("reenvio de convite revogado é rejeitado (409)")
    void shouldRejectResendOfRevokedInvitation() {
        UUID tenant = UUID.randomUUID();
        Invitation invitation = outstanding(tenant);
        invitation.markRevoked(Instant.now());
        when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.resend(tenant, invitation.getId()))
                .isInstanceOf(ConflictException.class);
        verify(mailer, never()).send(any());
    }

    @Test
    @DisplayName("revogar um convite em aberto marca revokedAt")
    void shouldRevokeOutstandingInvitation() {
        UUID tenant = UUID.randomUUID();
        Invitation invitation = outstanding(tenant);
        when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        service.revoke(tenant, invitation.getId());

        assertThat(invitation.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("revogar um convite já aceito é rejeitado (409), sem marcar revogação")
    void shouldRejectRevokeOfAcceptedInvitation() {
        UUID tenant = UUID.randomUUID();
        Invitation invitation = outstanding(tenant);
        invitation.markAccepted(Instant.now());
        when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.revoke(tenant, invitation.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(invitation.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("convite de outro tenant não é acessível: revogar retorna 404")
    void shouldReturn404WhenRevokingOtherTenantInvitation() {
        Invitation invitation = outstanding(UUID.randomUUID());
        when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), invitation.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(invitation.getRevokedAt()).isNull();
    }
}
