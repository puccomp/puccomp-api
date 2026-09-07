package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SelectionProcessService implements ProcessDirectory {

    private final SelectionProcessRepository repository;
    private final ApplicationCounts applicationCounts;

    @Transactional(readOnly = true)
    Page<SelectionProcessSummaryResponse> findAll(SelectionProcessStatus status, Pageable pageable) {
        Instant now = Instant.now();
        Page<SelectionProcess> page = pageOf(status, now, pageable);
        Map<UUID, ApplicationCounts.ApplicationStats> stats = statsFor(page.getContent().stream()
                .map(SelectionProcess::getId).toList());

        return page.map(process -> SelectionProcessSummaryResponse.from(process, statsOf(stats, process), now));
    }

    /**
     * O filtro casa com o status efetivo, não com o gravado — senão um processo cujo prazo venceu
     * sumiria de {@code IN_REVIEW} e apareceria em {@code OPEN}, contradizendo o que a resposta diz.
     */
    private Page<SelectionProcess> pageOf(SelectionProcessStatus status, Instant now, Pageable pageable) {
        if (status == null) return repository.findAll(pageable);
        return switch (status) {
            case OPEN -> repository.findEffectivelyOpen(now, pageable);
            case IN_REVIEW -> repository.findEffectivelyInReview(now, pageable);
            default -> repository.findByStatus(status, pageable);
        };
    }

    @Transactional(readOnly = true)
    SelectionProcessResponse findById(UUID id) {
        SelectionProcess process = findOwned(id);
        return SelectionProcessResponse.from(process, statsOf(statsFor(List.of(id)), process), Instant.now());
    }

    private Map<UUID, ApplicationCounts.ApplicationStats> statsFor(List<UUID> processIds) {
        return applicationCounts.statsByProcess(processIds);
    }

    private static ApplicationCounts.ApplicationStats statsOf(
            Map<UUID, ApplicationCounts.ApplicationStats> stats, SelectionProcess process) {
        return stats.getOrDefault(process.getId(), ApplicationCounts.ApplicationStats.NONE);
    }

    @Transactional
    SelectionProcessResponse create(SelectionProcessRequest request) {
        SelectionProcess process = SelectionProcess.builder()
                .title(request.title().trim())
                .status(SelectionProcessStatus.DRAFT)
                .build();
        process.update(request.title().trim(), trimmed(request.description()),
                request.opensAt(), request.closesAt(), request.resultAt(),
                request.minTerm(), request.maxTerm());

        return detailOf(repository.save(process), Instant.now());
    }

    @Transactional
    SelectionProcessResponse update(UUID id, SelectionProcessRequest request) {
        SelectionProcess process = findOwned(id);
        process.update(request.title().trim(), trimmed(request.description()),
                request.opensAt(), request.closesAt(), request.resultAt(),
                request.minTerm(), request.maxTerm());

        return detailOf(process, Instant.now());
    }

    @Transactional
    SelectionProcessResponse changeStatus(UUID id, SelectionProcessStatus status) {
        Instant now = Instant.now();
        SelectionProcess process = findOwned(id);
        process.changeStatusTo(status, now);
        return detailOf(process, now);
    }

    @Transactional(readOnly = true)
    List<PublicProcessResponse> listOpen() {
        Instant now = Instant.now();
        return repository.findByStatusOrderByCreatedAtDesc(SelectionProcessStatus.OPEN).stream()
                .filter(process -> process.isAcceptingApplications(now))
                .map(process -> PublicProcessResponse.from(process, now))
                .toList();
    }

    /**
     * A listagem pública mostra só quem aceita inscrição, mas o detalhe responde para qualquer
     * processo já publicado — encerrado inclusive. O candidato que voltar ao link depois do prazo
     * precisa ler as datas, não um 404.
     */
    @Transactional(readOnly = true)
    PublicProcessResponse findPublishedById(UUID id) {
        return repository.findPublished(id)
                .map(process -> PublicProcessResponse.from(process, Instant.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    /** Único ponto que decide se uma inscrição entra — por isso a janela é checada aqui, não no status. */
    @Override
    @Transactional(readOnly = true)
    public Optional<SelectionProcess> findOpen(UUID processId) {
        Instant now = Instant.now();
        return repository.findByIdAndStatus(processId, SelectionProcessStatus.OPEN)
                .filter(process -> process.isAcceptingApplications(now));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID processId) {
        return repository.existsById(processId);
    }

    private SelectionProcessResponse detailOf(SelectionProcess process, Instant at) {
        return SelectionProcessResponse.from(process,
                statsOf(statsFor(List.of(process.getId())), process), at);
    }

    private SelectionProcess findOwned(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
