package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.identity.account.LoginResponse;
import br.com.puccomp.api.identity.notification.Mailer;
import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import br.com.puccomp.api.shared.token.TokenSecrets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class InvitationService {

    private static final String PREFIX = "inv_";
    private static final int PREFIX_DISPLAY_LENGTH = 12;

    private final InvitationRepository repository;
    private final AccountRepository accounts;
    private final MemberProvisioning memberProvisioning;
    private final MemberDirectory memberDirectory;
    private final InvitationAcceptor acceptor;
    private final JwtService jwtService;
    private final Mailer mailer;
    private final OnboardingProperties properties;

    @Transactional
    InvitationResponse create(AuthPrincipal admin, CreateInvitationRequest request) {
        String email = request.email().trim();
        accounts.findByEmailIgnoreCase(email)
                .flatMap(account -> memberDirectory.findMembership(account.getId(), admin.tenantId()))
                .ifPresent(membership -> {
                    throw new ConflictException("Essa pessoa já é membro desta EJ");
                });

        Standing standing = request.standing() == null ? Standing.STAFF : request.standing();
        if (isPrivileged(standing) && !isPrivileged(admin.standing()))
            throw new AccessDeniedException("Apenas OWNER ou ADMIN podem convidar com esse standing");

        if (request.roleId() != null && !memberProvisioning.roleExists(request.roleId()))
            throw new ResourceNotFoundException("Cargo não encontrado");

        IssuedToken token = newToken();
        Invitation invitation = Invitation.builder()
                .tenantId(admin.tenantId())
                .email(email)
                .standing(standing)
                .roleId(request.roleId())
                .tokenHash(token.hash())
                .tokenPrefix(token.prefix())
                .expiresAt(Instant.now().plus(properties.invitationTtl()))
                .createdByAccountId(admin.accountId())
                .build();
        repository.save(invitation);

        sendInvitationEmail(email, token.raw());
        return InvitationResponse.from(invitation, Instant.now());
    }

    @Transactional(readOnly = true)
    Page<InvitationResponse> listOutstanding(UUID tenantId, Pageable pageable) {
        Instant now = Instant.now();
        return repository.findByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNull(tenantId, pageable)
                .map(invitation -> InvitationResponse.from(invitation, now));
    }

    @Transactional
    void revoke(UUID tenantId, UUID invitationId) {
        Invitation invitation = findOwned(tenantId, invitationId);
        if (invitation.getAcceptedAt() != null)
            throw new ConflictException("Convite já foi aceito e não pode ser revogado");
        if (invitation.getRevokedAt() == null)
            invitation.markRevoked(Instant.now());
    }

    @Transactional
    InvitationResponse resend(UUID tenantId, UUID invitationId) {
        Invitation invitation = findOwned(tenantId, invitationId);
        if (invitation.getAcceptedAt() != null)
            throw new ConflictException("Convite já foi aceito");
        if (invitation.getRevokedAt() != null)
            throw new ConflictException("Convite foi revogado; crie um novo");

        IssuedToken token = newToken();
        invitation.reissue(token.hash(), token.prefix(), Instant.now().plus(properties.invitationTtl()));

        sendInvitationEmail(invitation.getEmail(), token.raw());
        return InvitationResponse.from(invitation, Instant.now());
    }

    private static boolean isPrivileged(Standing standing) {
        return standing == Standing.OWNER || standing == Standing.ADMIN;
    }

    private Invitation findOwned(UUID tenantId, UUID invitationId) {
        return repository.findById(invitationId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Convite não encontrado"));
    }

    private IssuedToken newToken() {
        String raw = PREFIX + TokenSecrets.randomSecret();
        return new IssuedToken(raw, TokenSecrets.sha256Hex(raw), raw.substring(0, PREFIX_DISPLAY_LENGTH));
    }

    private record IssuedToken(String raw, String hash, String prefix) { }

    LoginResponse accept(AcceptInvitationRequest request) {
        Invitation invitation = repository.findByTokenHash(TokenSecrets.sha256Hex(request.token().trim()))
                .filter(i -> i.isUsable(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Convite inválido ou expirado"));

        TenantContext.set(invitation.getTenantId());
        var provisioned = acceptor.provision(invitation.getId(), request);

        return new LoginResponse(
                jwtService.generateAccessToken(provisioned.account(), invitation.getTenantId(),
                        provisioned.memberId(), provisioned.standing()),
                "Bearer",
                jwtService.accessTokenTtlSeconds());
    }

    private void sendInvitationEmail(String to, String rawToken) {
        String link = properties.acceptUrlBase() + "?token=" + rawToken;
        String body = """
                Você foi convidado para o sistema da sua Empresa Júnior.

                Para aceitar, acesse o link abaixo, defina sua senha e complete seu perfil:
                %s

                O convite expira em %d horas.""".formatted(link, properties.invitationTtl().toHours());
        mailer.send(properties.fromAddress(), to, "Convite para o sistema da sua EJ", body);
    }
}
