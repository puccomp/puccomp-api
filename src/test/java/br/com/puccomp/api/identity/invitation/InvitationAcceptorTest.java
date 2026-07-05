package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.account.*;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberDirectory.Membership;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.UnauthorizedException;
import br.com.puccomp.api.shared.reference.Course;
import br.com.puccomp.api.shared.reference.Standing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationAcceptorTest {

    @Mock private InvitationRepository repository;
    @Mock private AccountRepository accounts;
    @Mock private MemberProvisioning memberProvisioning;
    @Mock private MemberDirectory memberDirectory;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private InvitationAcceptor acceptor;

    private Invitation invitation(UUID tenantId, UUID roleId) {
        return Invitation.builder()
                .tenantId(tenantId)
                .email("novato@ej.dev")
                .standing(Standing.STAFF)
                .roleId(roleId)
                .tokenHash("hash")
                .tokenPrefix("inv_abc")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdByAccountId(UUID.randomUUID())
                .build();
    }

    private Account existingAccount() {
        return Account.builder()
                .id(UUID.randomUUID())
                .email("novato@ej.dev")
                .passwordHash("hash")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("sem conta prévia: cria conta global e membro com cargo e standing do convite")
    void shouldCreateAccountAndMember() {
        UUID invId = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Invitation invitation = invitation(tenant, cargo);
        when(repository.findById(invId)).thenReturn(Optional.of(invitation));
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("hash");
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberProvisioning.createMember(any(), eq("Novato"), eq(Course.COMPUTER_SCIENCE), eq(cargo),
                eq(Standing.STAFF))).thenReturn(memberId);

        InvitationAcceptor.Provisioned result = acceptor.provision(invId,
                new AcceptInvitationRequest("inv_token", "senha123", "Novato", Course.COMPUTER_SCIENCE));

        assertThat(invitation.getAcceptedAt()).isNotNull();
        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.standing()).isEqualTo(Standing.STAFF);
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("novato@ej.dev");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("conta já existe (multi-EJ): vincula o novo membro sem recriar a conta")
    void shouldLinkExistingAccountToNewTenant() {
        UUID invId = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Account existing = existingAccount();
        when(repository.findById(invId)).thenReturn(Optional.of(invitation(tenant, null)));
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(memberDirectory.findMembership(existing.getId(), tenant)).thenReturn(Optional.empty());
        when(memberProvisioning.createMember(eq(existing.getId()), eq("Novato"), eq(Course.COMPUTER_SCIENCE),
                isNull(), eq(Standing.STAFF))).thenReturn(memberId);

        InvitationAcceptor.Provisioned result = acceptor.provision(invId,
                new AcceptInvitationRequest("inv_token", "senha123", "Novato", Course.COMPUTER_SCIENCE));

        assertThat(result.memberId()).isEqualTo(memberId);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("conta existente já é membro deste tenant: rejeita (409), sem criar vínculo")
    void shouldRejectIfAlreadyMemberOfTenant() {
        UUID invId = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        Account existing = existingAccount();
        when(repository.findById(invId)).thenReturn(Optional.of(invitation(tenant, null)));
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(memberDirectory.findMembership(existing.getId(), tenant))
                .thenReturn(Optional.of(new Membership(UUID.randomUUID(), tenant, Standing.STAFF)));

        assertThatThrownBy(() -> acceptor.provision(invId,
                new AcceptInvitationRequest("inv_token", "senha123", "Novato", Course.COMPUTER_SCIENCE)))
                .isInstanceOf(ConflictException.class);
        verify(memberProvisioning, never()).createMember(any(), any(), any(), any(), any());
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("conta existente com senha errada: rejeita (401), sem criar vínculo")
    void shouldRejectWrongPasswordForExistingAccount() {
        UUID invId = UUID.randomUUID();
        Account existing = existingAccount();
        when(repository.findById(invId)).thenReturn(Optional.of(invitation(UUID.randomUUID(), null)));
        when(accounts.findByEmailIgnoreCase("novato@ej.dev")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(false);

        assertThatThrownBy(() -> acceptor.provision(invId,
                new AcceptInvitationRequest("inv_token", "senha123", "Novato", Course.COMPUTER_SCIENCE)))
                .isInstanceOf(UnauthorizedException.class);
        verify(memberProvisioning, never()).createMember(any(), any(), any(), any(), any());
        verify(accounts, never()).save(any());
    }
}
