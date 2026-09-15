package br.com.puccomp.api.notification;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.organization.MemberAssigned;
import br.com.puccomp.api.organization.MemberStatusChanged;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Avisa o membro sobre o que mudou no vínculo dele — sem isto, perder ou ganhar acesso acontece
 * em silêncio, e a pessoa só descobre na próxima vez que tenta usar o sistema.
 */
@Component
@RequiredArgsConstructor
class MembershipNotifications {

    private final Mailer mailer;
    private final TenantScope tenants;
    private final Recipients recipients;
    private final OrganizationDirectory organizations;

    @ApplicationModuleListener
    void on(MemberStatusChanged event) {
        tenants.run(event.tenantId(), () -> recipients.emailOf(event.accountId())
                .ifPresent(email -> mailer.deliver(new EmailMessage(
                        email,
                        subjectOf(event),
                        "vinculo-alterado",
                        Map.of(
                                "preheader", subjectOf(event),
                                "memberName", Recipients.firstName(event.memberName()),
                                "organizationName", organizations.currentName(),
                                "situation", situationOf(event),
                                "changedAt", OrganizationTime.display(event.at()))))));
    }

    @ApplicationModuleListener
    void on(MemberAssigned event) {
        tenants.run(event.tenantId(), () -> recipients.emailOf(event.accountId())
                .ifPresent(email -> mailer.deliver(new EmailMessage(
                        email,
                        "Sua atribuição mudou",
                        "cargo-atribuido",
                        Map.of(
                                "preheader", "Sua atribuição na " + organizations.currentName() + " mudou.",
                                "memberName", Recipients.firstName(event.memberName()),
                                "organizationName", organizations.currentName(),
                                "role", blankToDash(event.roleName()),
                                "department", blankToDash(event.departmentName()),
                                "changedAt", OrganizationTime.display(event.at()))))));
    }

    private static String subjectOf(MemberStatusChanged event) {
        return switch (event.transition()) {
            case RETIRED -> "Você agora é alumnus";
            case REACTIVATED -> "Seu vínculo foi reativado";
            case DEACTIVATED -> "Seu vínculo foi desativado";
        };
    }

    private static String situationOf(MemberStatusChanged event) {
        return switch (event.transition()) {
            case RETIRED -> "Seu vínculo ativo foi encerrado. Você continua com acesso de leitura ao "
                    + "histórico do que viveu na EJ, mas não pode mais alterar dados.";
            case REACTIVATED -> "Seu vínculo voltou a ser ativo. Você recupera o acesso que o seu "
                    + "cargo concede.";
            case DEACTIVATED -> "Seu vínculo foi desativado e o seu acesso à EJ foi suspenso.";
        };
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
