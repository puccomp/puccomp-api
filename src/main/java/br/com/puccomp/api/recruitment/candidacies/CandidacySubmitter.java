package br.com.puccomp.api.recruitment.candidacies;

import br.com.puccomp.api.recruitment.candidates.Candidate;
import br.com.puccomp.api.recruitment.candidates.CandidateRepository;
import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidacySubmitter {

    private final CandidacyRepository candidacies;
    private final CandidateRepository candidates;
    private final ProcessDirectory processes;

    @Transactional
    CandidacyReceiptResponse submit(UUID processId, SubmitCandidacyRequest request) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(
                        "Este processo seletivo não está aceitando inscrições no momento"));

        if (!process.acceptsCandidaciesAt(Instant.now()))
            throw new ConflictException("As inscrições deste processo seletivo estão fora do prazo");

        String email = request.email().trim();

        Candidate candidate = candidates.findByEmailIgnoreCase(email)
                .or(() -> {
                    if (isBlank(request.fullName()) || isBlank(request.phone())) {
                        throw new ResourceNotFoundException("Candidato não encontrado para o e-mail informado");
                    }

                    return Optional.of(candidates.save(
                            Candidate.builder()
                                    .fullName(request.fullName().trim())
                                    .email(email)
                                    .phone(request.phone().trim())
                                    .linkedinUrl(trimmed(request.linkedinUrl()))
                                    .portfolioUrl(trimmed(request.portfolioUrl()))
                                    .build()));
                })
                .orElseThrow(() -> new ResourceNotFoundException("Candidato não encontrado para o e-mail informado"));

        String fullName = firstNonBlank(request.fullName(), candidate.getFullName());
        String phone = firstNonBlank(request.phone(), candidate.getPhone());
        String course = firstNonBlank(request.course(), "Não informado");

        var candidacy = Candidacy.builder()
                .process(process)
                .candidate(candidate)
                .course(course)
                .currentTerm(request.currentTerm())
                .status(CandidacyStatus.SUBMITTED)
                .privacyConsentAt(Instant.now())
                .build();

        return CandidacyReceiptResponse.from(candidacies.save(candidacy));
    }

    private static String firstNonBlank(String primary, String fallback) {
        return isBlank(primary) ? fallback : primary.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
