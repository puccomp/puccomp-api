package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileService;
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
import java.util.Objects;
import java.util.UUID;

/** Parte transacional da inscrição, separada para que antivírus e S3 rodem sem transação aberta. */
@Service
@RequiredArgsConstructor
class CandidateApplicationRegistry {

    private static final String PROCESSO_FECHADO = "Este processo seletivo não está aceitando inscrições no momento";
    private static final String JA_INSCRITO = "Você já se inscreveu neste processo seletivo";

    private final CandidateApplicationRepository applications;
    private final ProcessDirectory processes;
    private final FileService files;

    /** O que o envio de email precisa, materializado antes da transação fechar. */
    record Registered(CandidateApplicationReceiptResponse receipt, String fullName, String email,
                      String course, String processTitle) { }

    @Transactional(readOnly = true)
    Page<CandidateApplicationResponse> listByProcess(UUID processId, Pageable pageable) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");

        var page = applications.findByProcessId(processId, pageable);
        var downloads = files.downloads(page.stream().map(CandidateApplication::getCvFileId)
                .filter(Objects::nonNull).toList());
        return page.map(application -> CandidateApplicationResponse.from(application,
                application.getCvFileId() == null ? null : downloads.get(application.getCvFileId())));
    }

    /** Falha cedo, antes de gastar antivírus e S3 num envio que já seria recusado. */
    @Transactional(readOnly = true)
    void requireSubmittable(UUID processId, String email) {
        if (processes.findOpen(processId).isEmpty()) throw new ConflictException(PROCESSO_FECHADO);
        if (applications.existsByProcessIdAndEmailIgnoreCase(processId, email)) throw new ConflictException(JA_INSCRITO);
    }

    @Transactional
    Registered register(UUID processId, SubmitCandidateApplicationRequest request, UUID cvFileId) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(PROCESSO_FECHADO));
        if (cvFileId != null) files.confirm(cvFileId);

        var application = CandidateApplication.builder()
                .process(process)
                .fullName(request.fullName().trim())
                .email(request.email().trim())
                .phone(request.phone().trim())
                .course(request.course().trim())
                .currentTerm(trimmed(request.currentTerm()))
                .cvFileId(cvFileId)
                .links(sanitized(request.links()))
                .privacyConsentAt(Instant.now())
                .build();

        CandidateApplication saved;
        try {
            saved = applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(JA_INSCRITO);
        }
        return new Registered(CandidateApplicationReceiptResponse.from(saved), saved.getFullName(),
                saved.getEmail(), saved.getCourse(), process.getTitle());
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> sanitized(List<String> links) {
        if (links == null) return List.of();
        return links.stream().map(String::trim).toList();
    }
}
