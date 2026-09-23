package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.files.FileMetadata;
import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.recruitment.ApplicationSubmitted;
import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CandidateApplicationRegistry {

    private static final String PROCESSO_FECHADO = "Este processo seletivo não está aceitando inscrições no momento";
    private static final String JA_INSCRITO = "Você já se inscreveu neste processo seletivo";

    private final CandidateApplicationRepository applications;
    private final ProcessDirectory processes;
    private final CourseCatalog courses;
    private final FileService files;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    Page<SignedCandidateApplicationResponse> listByProcessSigned(UUID processId, CandidateApplicationFilter filter,
                                                                 Pageable pageable) {
        requireProcess(processId);
        return presentSigned(applications.findAll(CandidateApplicationSpecs.matching(processId, filter), pageable));
    }

    /** Sem processId no recorte: o filtro de tenant do Hibernate já limita à EJ de quem chama. */
    @Transactional(readOnly = true)
    Page<CandidateApplicationResponse> searchAcrossProcesses(CandidateApplicationFilter filter, Pageable pageable) {
        Page<CandidateApplication> page = applications.findAll(CandidateApplicationSpecs.matching(null, filter), pageable);
        return page.map(presenter(page.getContent(), files.metadata(cvIds(page.getContent()))));
    }

    @Transactional(readOnly = true)
    Page<SignedCandidateApplicationResponse> searchAcrossProcessesSigned(CandidateApplicationFilter filter,
                                                                         Pageable pageable) {
        return presentSigned(applications.findAll(CandidateApplicationSpecs.matching(null, filter), pageable));
    }

    @Transactional(readOnly = true)
    CandidateApplicationResponse findById(UUID applicationId) {
        CandidateApplication application = findOwned(applicationId);
        List<CandidateApplication> single = List.of(application);
        return presenter(single, files.metadata(cvIds(single))).apply(application);
    }

    /** Assina a cada chamada: a URL vence em minutos, e quem pede está prestes a abrir o arquivo. */
    @Transactional(readOnly = true)
    FileDownload cvOf(UUID applicationId) {
        UUID cvFileId = findOwned(applicationId).getCvFileId();
        if (cvFileId == null)
            throw new ResourceNotFoundException("Esta inscrição não tem currículo");
        FileDownload download = files.downloads(List.of(cvFileId)).get(cvFileId);
        if (download == null)
            throw new ResourceNotFoundException("Currículo não encontrado");
        return download;
    }

    private CandidateApplication findOwned(UUID applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada"));
    }

    private void requireProcess(UUID processId) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");
    }

    private Page<SignedCandidateApplicationResponse> presentSigned(Page<CandidateApplication> page) {
        Map<UUID, FileDownload> downloads = files.downloads(cvIds(page.getContent()));
        var present = presenter(page.getContent(), downloads.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().metadata())));
        return page.map(application -> SignedCandidateApplicationResponse.from(present.apply(application),
                application.getCvFileId() == null ? null : downloads.get(application.getCvFileId())));
    }

    /** Curso, currículo e histórico resolvidos em lote para todas as inscrições de uma vez. */
    private Function<CandidateApplication, CandidateApplicationResponse> presenter(
            List<CandidateApplication> page, Map<UUID, FileMetadata> cvs) {
        Map<UUID, String> courseNames = courses.namesOf(
                page.stream().map(application -> application.getCourseId()).toList());
        Map<String, CandidateApplicationResponse.History> histories = historiesOf(page);
        return application -> CandidateApplicationResponse.from(application,
                application.getCvFileId() == null ? null : cvs.get(application.getCvFileId()),
                courseNames.get(application.getCourseId()),
                historyOf(application, histories));
    }

    private static List<UUID> cvIds(List<CandidateApplication> applications) {
        return applications.stream().map(application -> application.getCvFileId())
                .filter(Objects::nonNull).toList();
    }

    /**
     * Uma consulta agrupada para a página inteira, ao lado de currículo e curso: por linha seriam
     * tantas consultas quanto inscrições, para um dado que a triagem lê em toda linha.
     */
    private Map<String, CandidateApplicationResponse.History> historiesOf(List<CandidateApplication> page) {
        List<String> emails = page.stream().map(application -> key(application.getEmail())).distinct().toList();
        if (emails.isEmpty()) return Map.of();
        return applications.aggregateByEmails(emails).stream()
                .collect(Collectors.toMap(
                        row -> row.getEmail(),
                        row -> new CandidateApplicationResponse.History(row.getTotal(),
                                row.getFirstAppliedAt()),
                        (a, b) -> a));
    }

    /** Sem linha correspondente, a inscrição em mãos é todo o histórico que se pode afirmar. */
    private static CandidateApplicationResponse.History historyOf(CandidateApplication application,
            Map<String, CandidateApplicationResponse.History> histories) {
        return histories.getOrDefault(key(application.getEmail()),
                new CandidateApplicationResponse.History(1, application.getCreatedAt()));
    }

    /** A mesma normalização do {@code lower(email)} do agrupamento, para as chaves baterem. */
    private static String key(String email) {
        return email.toLowerCase(Locale.ROOT);
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

    /**
     * Revalida o que {@code requireSubmittable} já checou, de propósito: entre as duas chamadas
     * rodam antivírus e upload, dezenas de segundos em que o prazo vence ou o curso sai do catálogo.
     * A checagem de antes evita gastar S3 à toa; esta, dentro da transação, é a que decide.
     */
    @Transactional
    CandidateApplicationReceiptResponse register(UUID processId, SubmitCandidateApplicationRequest request,
                                                 UUID cvFileId) {
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

        // Dentro da transação: a publicação vai para o outbox junto com a inscrição, ou nenhuma
        // das duas. Sem transação ativa, um @TransactionalEventListener sequer é chamado.
        var receipt = CandidateApplicationReceiptResponse.from(saved);
        events.publishEvent(new ApplicationSubmitted(saved.getTenantId(), saved.getId(), processId,
                process.getTitle(), saved.getFullName(), saved.getEmail(), courseName,
                cvFileId != null, receipt.submittedAt()));
        return receipt;
    }

    private static List<String> sanitized(List<String> links) {
        if (links == null) return List.of();
        return links.stream().map(value -> value.trim()).toList();
    }
}
