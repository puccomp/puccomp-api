package br.com.puccomp.api.notification;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.identity.InvitationAccepted;
import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fecha o ciclo do convite para quem o enviou — sem isto, só se descobre que deu certo voltando à
 * lista de pendentes e reparando que um sumiu.
 */
@Component
@RequiredArgsConstructor
class OnboardingNotifications {

    private final Mailer mailer;
    private final TenantScope tenants;
    private final Recipients recipients;
    private final OrganizationDirectory organizations;

    @ApplicationModuleListener
    void on(InvitationAccepted event) {
        tenants.run(event.tenantId(), () -> recipients.emailOf(event.inviterAccountId())
                .ifPresent(inviter -> mailer.deliver(new EmailMessage(
                        inviter,
                        "Convite aceito: " + event.inviteeName(),
                        "convite-aceito",
                        Map.of(
                                "preheader", event.inviteeName() + " entrou para a "
                                        + organizations.currentName() + ".",
                                "inviteeName", event.inviteeName(),
                                "inviteeEmail", event.inviteeEmail(),
                                "organizationName", organizations.currentName(),
                                "acceptedAt", OrganizationTime.display(event.at()))))));
    }
}
