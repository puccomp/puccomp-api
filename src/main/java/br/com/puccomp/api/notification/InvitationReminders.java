package br.com.puccomp.api.notification;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.identity.InvitationDirectory;
import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Lembra quem convidou que o convite está para expirar sem resposta.
 *
 * <p>Vai para quem convidou, e não para o convidado, porque o token é guardado como hash: um
 * lembrete ao convidado não teria como levar um link. Quem convidou pode reenviar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InvitationReminders {

    /**
     * A janela tem a largura do intervalo entre execuções: cada convite atravessa uma fatia só, e
     * é lembrado uma vez, sem coluna que registre o envio.
     */
    private static final Duration SLICE = Duration.ofHours(1);

    private final InvitationDirectory invitations;
    private final Mailer mailer;
    private final TenantScope tenants;
    private final Recipients recipients;
    private final OrganizationDirectory organizations;
    private final NotificationProperties properties;

    @Scheduled(cron = "${puccomp.notification.reminder-cron:0 5 * * * *}",
            zone = "America/Sao_Paulo")
    void remind() {
        Instant from = Instant.now().plus(properties.invitationReminder());
        for (var invitation : invitations.expiringBetween(from, from.plus(SLICE))) {
            try {
                tenants.run(invitation.tenantId(), () -> notifyInviter(invitation));
            } catch (RuntimeException e) {
                log.error("Falha ao lembrar do convite {}: {}", invitation.id(), e.getMessage(), e);
            }
        }
    }

    private void notifyInviter(InvitationDirectory.PendingInvitation invitation) {
        recipients.emailOf(invitation.inviterAccountId()).ifPresent(inviter ->
                mailer.deliver(new EmailMessage(
                        inviter,
                        "Convite prestes a expirar: " + invitation.email(),
                        "convite-expirando",
                        Map.of(
                                "preheader", "O convite para " + invitation.email()
                                        + " expira em breve e ainda não foi aceito.",
                                "inviteeEmail", invitation.email(),
                                "organizationName", organizations.currentName(),
                                "expiresAt", OrganizationTime.display(invitation.expiresAt())))));
    }
}
