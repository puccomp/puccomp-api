package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.exception.ValidationException;
import br.com.puccomp.api.shared.reference.NamedRef;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
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
    Page<CandidateApplicationResponse> listByProcess(UUID processId, CandidateApplicationFilter filter,
                                                     Pageable pageable) {
        requireProcess(processId);
        return present(applications.findAll(CandidateApplicationSpecs.matching(processId, filter), pageable));
    }

    /** Sem processId no recorte: o filtro de tenant do Hibernate já limita à EJ de quem chama. */
    @Transactional(readOnly = true)
    Page<CandidateApplicationResponse> searchAcrossProcesses(CandidateApplicationFilter filter, Pageable pageable) {
        return present(applications.findAll(CandidateApplicationSpecs.matching(null, filter), pageable));
    }

    @Transactional(readOnly = true)
    ApplicationSummaryResponse summarize(UUID processId, ZoneId zone) {
        requireProcess(processId);
        var totals = applications.totalsByProcess(processId);
        var byDay = applications.countByDay(processId, zone.getId()).stream()
                .map(row -> new ApplicationSummaryResponse.DayCount(row.getDay(), row.getTotal()))
                .toList();
        var byTerm = applications.countByTerm(processId).stream()
                .map(row -> new ApplicationSummaryResponse.TermCount(row.getTerm(), row.getTotal()))
                .toList();

        var counts = applications.countByCourse(processId);
        Map<UUID, String> names = courses.namesOf(counts.stream()
                .map(CandidateApplicationRepository.CourseCountRow::getCourseId).toList());
        var byCourse = counts.stream()
                .map(row -> new ApplicationSummaryResponse.CourseCount(
                        NamedRef.of(row.getCourseId(), names.get(row.getCourseId())), row.getTotal()))
                .toList();

        long total = totals == null ? 0 : totals.getTotal();
        var peak = byDay.stream().max(Comparator.comparingLong(ApplicationSummaryResponse.DayCount::count))
                .orElse(null);
        Double lastDayShare = total == 0 || byDay.isEmpty() ? null
                : (double) byDay.getLast().count() / total;

        return new ApplicationSummaryResponse(
                processId,
                total,
                totals == null ? 0 : totals.getWithCv(),
                totals == null || totals.getWithLinks() == null ? 0 : totals.getWithLinks(),
                totals == null ? null : totals.getFirstSubmittedAt(),
                totals == null ? null : totals.getLastSubmittedAt(),
                byCourse,
                byTerm,
                byDay,
                peak,
                lastDayShare,
                byCourse.size(),
                medianTerm(byTerm));
    }

    /** Mediana a partir das contagens já agrupadas — quem não informou período fica de fora. */
    private static Short medianTerm(List<ApplicationSummaryResponse.TermCount> byTerm) {
        List<ApplicationSummaryResponse.TermCount> informed = byTerm.stream()
                .filter(t -> t.term() != null).toList();
        long informedTotal = informed.stream().mapToLong(ApplicationSummaryResponse.TermCount::count).sum();
        if (informedTotal == 0) return null;

        long middle = (informedTotal + 1) / 2;
        long running = 0;
        for (var entry : informed) {
            running += entry.count();
            if (running >= middle) return entry.term();
        }
        return null;
    }

    private void requireProcess(UUID processId) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");
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
