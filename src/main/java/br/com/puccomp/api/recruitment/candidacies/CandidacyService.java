package br.com.puccomp.api.recruitment.candidacies;

import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidacyService {

    private final CandidacyRepository candidacies;
    private final ProcessDirectory processes;
    private final CandidacySubmitter submitter;

    @Transactional(readOnly = true)
    Page<CandidacyResponse> listByProcess(UUID processId, Pageable pageable) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");

        return candidacies.findByProcessId(processId, pageable).map(CandidacyResponse::from);
    }

    /**
     * Sem {@code @Transactional} de propósito: a violação do índice único só estoura no commit de
     * {@link CandidacySubmitter#submit}, então só dá para traduzi-la em 409 de fora daquela transação.
     */
    CandidacyReceiptResponse submit(UUID processId, SubmitCandidacyRequest request) {
        try {
            return submitter.submit(processId, request);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Você já se inscreveu neste processo seletivo");
        }
    }
}
