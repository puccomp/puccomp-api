package br.com.puccomp.api.recruitment.candidacies;

import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidacySubmitter {

    private final CandidacyRepository candidacies;
    private final ProcessDirectory processes;

    @Transactional
    CandidacyReceiptResponse submit(UUID processId, SubmitCandidacyRequest request) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(
                        "Este processo seletivo não está aceitando inscrições no momento"));

        if (!process.acceptsCandidaciesAt(Instant.now()))
            throw new ConflictException("As inscrições deste processo seletivo estão fora do prazo");

        String email = request.email().trim();
        if (candidacies.existsByProcessIdAndEmailIgnoreCase(processId, email))
            throw new ConflictException("Você já se inscreveu neste processo seletivo");

        var candidacy = Candidacy.builder()
                .process(process)
                .fullName(request.fullName().trim())
                .email(email)
                .phone(request.phone().trim())
                .course(request.course().trim())
                .currentTerm(request.currentTerm().trim())
                .linkedinUrl(trimmed(request.linkedinUrl()))
                .portfolioUrl(trimmed(request.portfolioUrl()))
                .status(CandidacyStatus.SUBMITTED)
                .privacyConsentAt(Instant.now())
                .build();

        return CandidacyReceiptResponse.from(candidacies.save(candidacy));
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
