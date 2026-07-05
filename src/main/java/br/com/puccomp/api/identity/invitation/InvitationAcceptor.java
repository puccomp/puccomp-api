package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.account.Account;
import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AccountStatus;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.UnauthorizedException;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class InvitationAcceptor {

    private final InvitationRepository repository;
    private final AccountRepository accounts;
    private final MemberProvisioning memberProvisioning;
    private final MemberDirectory memberDirectory;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    Provisioned provision(UUID invitationId, AcceptInvitationRequest request) {
        var invitation = repository.findById(invitationId)
                .filter(i -> i.isUsable(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Convite inválido ou expirado"));

        var account = accounts.findByEmailIgnoreCase(invitation.getEmail())
                .map(existing -> linkExisting(existing, request, invitation.getTenantId()))
                .orElseGet(() -> accounts.save(Account.builder()
                        .email(invitation.getEmail())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .status(AccountStatus.ACTIVE)
                        .build()));

        var memberId = memberProvisioning.createMember(
                account.getId(), request.name().trim(), request.course(), invitation.getRoleId(),
                invitation.getStanding());

        invitation.markAccepted(Instant.now());
        return new Provisioned(account, memberId, invitation.getStanding());
    }

    private Account linkExisting(Account account, AcceptInvitationRequest request, UUID tenantId) {
        if (!account.isActive() || !passwordEncoder.matches(request.password(), account.getPasswordHash()))
            throw new UnauthorizedException("Não foi possível vincular a conta existente");
        if (memberDirectory.findMembership(account.getId(), tenantId).isPresent())
            throw new ConflictException("Você já é membro desta EJ");
        return account;
    }

    record Provisioned(Account account, UUID memberId, Standing standing) { }
}
