package br.com.puccomp.api.notification;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.recruitment.ApplicationSubmitted;
import br.com.puccomp.api.recruitment.CandidateDirectory;
import br.com.puccomp.api.recruitment.SelectionProcessPhaseChanged;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Avisos do funil de recrutamento, dos dois lados: o candidato e quem cuida do processo.
 *
 * <p>A confirmação da inscrição tem destinatário único e entrega durável ({@code deliver}). O que
 * é fan-out vai em melhor esforço ({@code send}): reprocessar uma falha no meio de centenas de
 * envios reenviaria para quem já recebeu.
 */
@Component
@RequiredArgsConstructor
class RecruitmentNotifications {

    private static final String RECRUITMENT_READ = "recruitment:read";

    private final Mailer mailer;
    private final TenantScope tenants;
    private final AudienceNotifier notifier;
    private final OrganizationDirectory organizations;
    private final CandidateDirectory candidates;
    private final NotificationProperties properties;

    @ApplicationModuleListener
    void on(ApplicationSubmitted event) {
        tenants.run(event.tenantId(), () -> {
            confirmToCandidate(event);
            if (properties.newApplication() == NotificationProperties.ArrivalMode.IMMEDIATE)
                notifyRecruiters(event);
        });
    }

    /** A mudança de fase é o único momento em que a EJ tem algo novo a dizer a quem se inscreveu. */
    @ApplicationModuleListener
    void on(SelectionProcessPhaseChanged event) {
        tenants.run(event.tenantId(), () -> {
            switch (event.phase()) {
                case OPENED -> notifyRecruitersOfOpening(event);
                case IN_REVIEW -> notifyCandidates(event, "inscricoes-encerradas",
                        "Inscrições encerradas: " + event.processTitle());
                case CLOSED -> notifyCandidates(event, "resultado-disponivel",
                        "Resultado disponível: " + event.processTitle());
                case CANCELLED -> notifyCandidates(event, "processo-cancelado",
                        "Processo seletivo cancelado: " + event.processTitle());
            }
        });
    }

    private void confirmToCandidate(ApplicationSubmitted event) {
        mailer.deliver(new EmailMessage(
                event.candidateEmail(),
                "Inscrição confirmada: " + event.processTitle(),
                "candidatura-confirmada",
                Map.of(
                        "preheader", "Recebemos sua inscrição em " + event.processTitle() + ".",
                        "candidateName", Recipients.firstName(event.candidateName()),
                        "processTitle", event.processTitle(),
                        "organizationName", organizations.currentName(),
                        "submittedAt", OrganizationTime.display(event.submittedAt()),
                        "protocol", event.applicationId().toString())));
    }

    private void notifyRecruiters(ApplicationSubmitted event) {
        notifier.notifyPermissionHolders(RECRUITMENT_READ,
                "Nova inscrição: " + event.processTitle(),
                "nova-inscricao",
                Map.of(
                        "preheader", event.candidateName() + " se inscreveu em " + event.processTitle() + ".",
                        "candidateName", event.candidateName(),
                        "candidateEmail", event.candidateEmail(),
                        "course", event.courseName(),
                        "processTitle", event.processTitle(),
                        "organizationName", organizations.currentName(),
                        "submittedAt", OrganizationTime.display(event.submittedAt()),
                        "cv", event.hasCv() ? "Enviado" : "Não enviado"));
    }

    private void notifyRecruitersOfOpening(SelectionProcessPhaseChanged event) {
        notifier.notifyPermissionHolders(RECRUITMENT_READ,
                "Inscrições abertas: " + event.processTitle(),
                "processo-aberto",
                Map.of(
                        "preheader", event.processTitle() + " está recebendo inscrições.",
                        "processTitle", event.processTitle(),
                        "organizationName", organizations.currentName(),
                        "openedAt", OrganizationTime.display(event.at())));
    }

    /** Um e-mail por pessoa, e não por inscrição. */
    private void notifyCandidates(SelectionProcessPhaseChanged event, String template, String subject) {
        var organization = organizations.currentName();
        var resultAt = event.resultAt() == null ? "" : OrganizationTime.display(event.resultAt());
        for (var candidate : candidates.candidatesOf(event.tenantId(), event.processId()))
            mailer.send(new EmailMessage(candidate.email(), subject, template,
                    Map.of(
                            "preheader", subject,
                            "candidateName", Recipients.firstName(candidate.name()),
                            "processTitle", event.processTitle(),
                            "organizationName", organization,
                            "resultAt", resultAt)));
    }
}
