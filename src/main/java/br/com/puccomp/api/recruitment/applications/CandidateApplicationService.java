package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidateApplicationService {

    private final CandidateApplicationRepository applications;
    private final ProcessDirectory processes;
    private final Mailer mailer;

    @Transactional(readOnly = true)
    Page<CandidateApplicationResponse> listByProcess(UUID processId, Pageable pageable) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");

        return applications.findByProcessId(processId, pageable).map(CandidateApplicationResponse::from);
    }

    @Transactional
    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(
                        "Este processo seletivo não está aceitando inscrições no momento"));

        var application = CandidateApplication.builder()
                .process(process)
                .fullName(request.fullName().trim())
                .email(request.email().trim())
                .phone(request.phone().trim())
                .course(request.course().trim())
                .currentTerm(trimmed(request.currentTerm()))
                .links(sanitized(request.links()))
                .privacyConsentAt(Instant.now())
                .build();

        CandidateApplication saved;
        try {
            saved = applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Você já se inscreveu neste processo seletivo");
        }

        mailer.send(new EmailMessage(
                saved.getEmail(),
                "Inscrição confirmada — " + process.getTitle(),
                "candidatura-confirmada",
                Map.of(
                        "candidateName", firstName(saved.getFullName()),
                        "processTitle", process.getTitle())));
        return CandidateApplicationReceiptResponse.from(saved);
    }

    private static String firstName(String fullName) {
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> sanitized(List<String> links) {
        if (links == null) return List.of();
        return links.stream().map(String::trim).toList();
    }
}
