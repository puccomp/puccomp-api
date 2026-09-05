package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.files.FileUpload;
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

    private final CandidateApplicationRegistry registry;
    private final AudienceNotifier notifier;
    private final Mailer mailer;
    private final FileService files;

    Page<CandidateApplicationResponse> listByProcess(UUID processId, Pageable pageable) {
        return registry.listByProcess(processId, pageable);
    }

    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request) {
        return submit(processId, request, null);
    }

    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request,
                                               FileUpload cv) {
        registry.requireSubmittable(processId, request.email().trim());
        // Antivírus e S3 levam dezenas de segundos: rodam sem transação aberta, e o arquivo só
        // vira currículo válido no register. Reserva abandonada é recolhida pela limpeza.
        UUID cvFileId = cv == null ? null : files.stage(cv);

        var registered = registry.register(processId, request, cvFileId);
        confirmToCandidate(registered);
        notifyRecruiters(registered);
        return registered.receipt();
    }

    private void confirmToCandidate(CandidateApplicationRegistry.Registered registered) {
        mailer.send(new EmailMessage(
                registered.email(),
                "Inscrição confirmada — " + registered.processTitle(),
                "candidatura-confirmada",
                Map.of(
                        "candidateName", firstName(registered.fullName()),
                        "processTitle", registered.processTitle())));
    }

    private void notifyRecruiters(CandidateApplicationRegistry.Registered registered) {
        notifier.notifyPermissionHolders(RECRUITMENT_READ,
                "Nova inscrição — " + registered.processTitle(),
                "nova-inscricao",
                Map.of(
                        "candidateName", registered.fullName(),
                        "candidateEmail", registered.email(),
                        "course", registered.course(),
                        "processTitle", registered.processTitle()));
    }

    private static String firstName(String fullName) {
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }
}
