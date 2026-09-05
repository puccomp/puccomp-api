package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final CourseCatalog courses;
    private final FileService files;

    /** O que o envio de email precisa, materializado antes da transação fechar. */
    record Registered(CandidateApplicationReceiptResponse receipt, String fullName, String email,
                      String course, String processTitle) { }

    @Transactional(readOnly = true)
    Page<CandidateApplicationResponse> listByProcess(UUID processId, String query, Pageable pageable) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");

        var page = SearchTerm.like(query)
                .map(term -> applications.searchByProcessId(processId, term, pageable))
                .orElseGet(() -> applications.findByProcessId(processId, pageable));
        return present(page);
    }

    @Transactional(readOnly = true)
    Page<CandidateApplicationResponse> searchAcrossProcesses(String query, Pageable pageable) {
        var page = SearchTerm.like(query)
                .map(term -> applications.search(term, pageable))
                .orElseGet(() -> applications.findBy(pageable));
        return present(page);
    }

    private Page<CandidateApplicationResponse> present(Page<CandidateApplication> page) {
        var downloads = files.downloads(page.stream().map(CandidateApplication::getCvFileId)
                .filter(Objects::nonNull).toList());
        Map<UUID, String> courseNames = courses.namesOf(
                page.stream().map(CandidateApplication::getCourseId).toList());
        return page.map(application -> CandidateApplicationResponse.from(application,
                application.getCvFileId() == null ? null : downloads.get(application.getCvFileId()),
                courseNames.get(application.getCourseId())));
    }

    /** Falha cedo, antes de gastar antivírus e S3 num envio que já seria recusado. */
    @Transactional(readOnly = true)
    void requireSubmittable(UUID processId, SubmitCandidateApplicationRequest request) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(PROCESSO_FECHADO));
        requireAcceptedCourse(request.courseId());
        requireEligibleTerm(process, request.currentTerm());
        if (applications.existsByProcessIdAndEmailIgnoreCase(processId, request.email().trim()))
            throw new ConflictException(JA_INSCRITO);
    }

    /**
     * Curso desativado deixa de ser aceito daqui pra frente sem invalidar quem já se inscreveu com
     * ele — é exatamente o que a desativação (em vez de remoção) do catálogo existe para permitir.
     */
    private void requireAcceptedCourse(UUID courseId) {
        if (!courses.isAssignable(courseId))
            throw new ValidationException("Este curso não é aceito por esta empresa júnior");
    }

    private static void requireEligibleTerm(SelectionProcess process, Short currentTerm) {
        if (process.acceptsTerm(currentTerm)) return;
        throw new ValidationException("Este processo seletivo aceita candidaturas %s"
                .formatted(termRange(process)));
    }

    private static String termRange(SelectionProcess process) {
        Short min = process.getMinTerm();
        Short max = process.getMaxTerm();
        if (min != null && max != null) return "do %dº ao %dº período".formatted(min, max);
        if (min != null) return "a partir do %dº período".formatted(min);
        return "até o %dº período".formatted(max);
    }

    @Transactional
    Registered register(UUID processId, SubmitCandidateApplicationRequest request, UUID cvFileId) {
        SelectionProcess process = processes.findOpen(processId)
                .orElseThrow(() -> new ConflictException(PROCESSO_FECHADO));
        requireAcceptedCourse(request.courseId());
        requireEligibleTerm(process, request.currentTerm());
        if (cvFileId != null) files.confirm(cvFileId);

        var application = CandidateApplication.builder()
                .process(process)
                .fullName(request.fullName().trim())
                .email(request.email().trim())
                .phone(request.phone().trim())
                .courseId(request.courseId())
                .currentTerm(request.currentTerm())
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
        String courseName = courses.namesOf(List.of(saved.getCourseId()))
                .getOrDefault(saved.getCourseId(), "");
        return new Registered(CandidateApplicationReceiptResponse.from(saved), saved.getFullName(),
                saved.getEmail(), courseName, process.getTitle());
    }

    private static List<String> sanitized(List<String> links) {
        if (links == null) return List.of();
        return links.stream().map(String::trim).toList();
    }
}
