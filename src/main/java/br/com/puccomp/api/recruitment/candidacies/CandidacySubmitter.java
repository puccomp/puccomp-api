package br.com.puccomp.api.recruitment.candidacies;

import br.com.puccomp.api.recruitment.candidates.Candidate;
import br.com.puccomp.api.recruitment.candidates.CandidateRegistry;
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
    private final CandidateRegistry candidates;
    private final ProcessDirectory processes;

    @Transactional
    CandidacyReceiptResponse submit(UUID processId, SubmitCandidacyRequest request) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(
                        "Este processo seletivo não está aceitando inscrições no momento"));

        if (!process.acceptsCandidaciesAt(Instant.now()))
            throw new ConflictException("As inscrições deste processo seletivo estão fora do prazo");

        Candidate candidate = candidates.findOrRegister(new CandidateRegistry.NewCandidate(
                request.fullName(), request.email(), request.phone(), request.links()));

        var candidacy = Candidacy.builder()
                .process(process)
                .candidate(candidate)
                .course(request.course().trim())
                .currentTerm(trimmedTerm(request.currentTerm()))
                .status(CandidacyStatus.SUBMITTED)
                .privacyConsentAt(Instant.now())
                .build();

        return CandidacyReceiptResponse.from(candidacies.save(candidacy));
    }

    private static String trimmedTerm(String currentTerm) {
        return currentTerm == null || currentTerm.isBlank() ? null : currentTerm.trim();
    }
}
