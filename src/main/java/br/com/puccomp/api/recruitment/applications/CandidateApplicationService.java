package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.files.FileUpload;
import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.notification.AudienceNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidateApplicationService {

    private static final String RECRUITMENT_READ = "recruitment:read";

    /** Agrupar a curva de chegada em UTC empurraria o fim da noite para o dia seguinte,
     *  bem onde o pico de prazo acontece. Vira configuração por EJ quando houver a primeira de fora. */
    private static final java.time.ZoneId EJ_ZONE = java.time.ZoneId.of("America/Sao_Paulo");

    private static final java.time.format.DateTimeFormatter DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final CandidateApplicationRegistry registry;
    private final ApplicationSummaryService summaries;
    private final AudienceNotifier notifier;
    private final Mailer mailer;
    private final FileService files;
    private final OrganizationDirectory organizations;

    Page<CandidateApplicationResponse> listByProcess(UUID processId, CandidateApplicationFilter filter,
                                                     Pageable pageable) {
        return registry.listByProcess(processId, filter, pageable);
    }

    Page<CandidateApplicationResponse> searchAcrossProcesses(CandidateApplicationFilter filter, Pageable pageable) {
        return registry.searchAcrossProcesses(filter, pageable);
    }

    ApplicationSummaryResponse summarize(UUID processId, CandidateApplicationFilter filter) {
        return summaries.summarize(processId, filter, EJ_ZONE);
    }

    ApplicationHistorySummaryResponse summarizeHistory(CandidateApplicationFilter filter) {
        return summaries.summarizeHistory(filter, EJ_ZONE);
    }

    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request) {
        return submit(processId, request, null);
    }

    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request,
                                               FileUpload cv) {
        registry.requireSubmittable(processId, request);
        // Antivírus e S3 levam dezenas de segundos: rodam sem transação aberta, e o arquivo só
        // vira currículo válido no register. Reserva abandonada é recolhida pela limpeza.
        UUID cvFileId = cv == null ? null : files.stage(cv);

        var registered = registry.register(processId, request, cvFileId);
        confirmToCandidate(registered);
        notifyRecruiters(registered, cvFileId != null);
        return registered.receipt();
    }

    private void confirmToCandidate(CandidateApplicationRegistry.Registered registered) {
        mailer.send(new EmailMessage(
                registered.email(),
                "Inscrição confirmada: " + registered.processTitle(),
                "candidatura-confirmada",
                Map.of(
                        "candidateName", firstName(registered.fullName()),
                        "processTitle", registered.processTitle(),
                        "organizationName", organizations.currentName(),
                        "submittedAt", formatted(registered.receipt().submittedAt()),
                        "protocol", registered.receipt().id().toString())));
    }

    private void notifyRecruiters(CandidateApplicationRegistry.Registered registered, boolean hasCv) {
        notifier.notifyPermissionHolders(RECRUITMENT_READ,
                "Nova inscrição: " + registered.processTitle(),
                "nova-inscricao",
                Map.of(
                        "candidateName", registered.fullName(),
                        "candidateEmail", registered.email(),
                        "course", registered.course(),
                        "processTitle", registered.processTitle(),
                        "organizationName", organizations.currentName(),
                        "submittedAt", formatted(registered.receipt().submittedAt()),
                        "cv", hasCv ? "Enviado" : "Não enviado"));
    }

    /** No fuso da EJ: quem lê "23:58" precisa que seja a hora do prazo, não a de Greenwich. */
    private static String formatted(java.time.Instant instant) {
        return DATE_TIME.format(instant.atZone(EJ_ZONE));
    }

    private static String firstName(String fullName) {
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }
}
