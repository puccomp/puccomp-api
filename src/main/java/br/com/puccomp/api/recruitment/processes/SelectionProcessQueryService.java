package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.text.SearchTerm;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A leitura dos processos, separada da escrita porque só ela precisa de {@link ApplicationCounts}:
 * a contagem de inscrições é contexto de consulta, não parte do estado que uma alteração devolve.
 */
@Service
@RequiredArgsConstructor
public class SelectionProcessQueryService implements ProcessDirectory {

    private final SelectionProcessRepository repository;
    private final ApplicationCounts applicationCounts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<SelectionProcessListItemResponse> findAll(SelectionProcessStatus status, String query,
                                                          Pageable pageable) {
        Instant now = clock.instant();
        Page<SelectionProcess> page = pageOf(status, SearchTerm.like(query), now, pageable);
        Map<UUID, ApplicationCounts.ApplicationStats> stats = applicationCounts.statsByProcess(
                page.getContent().stream().map(SelectionProcess::getId).toList());

        return page.map(process -> SelectionProcessListItemResponse.from(process, statsOf(stats, process), now));
    }

    /**
     * O filtro casa com o status efetivo, não com o gravado — senão um processo cujo prazo venceu
     * sumiria de {@code IN_REVIEW} e apareceria em {@code OPEN}, contradizendo o que a resposta diz.
     */
    private Page<SelectionProcess> pageOf(SelectionProcessStatus status, Optional<String> term,
                                          Instant now, Pageable pageable) {
        if (term.isEmpty()) {
            if (status == null) return repository.findAll(pageable);
            return switch (status) {
                case OPEN -> repository.findEffectivelyOpen(now, pageable);
                case IN_REVIEW -> repository.findEffectivelyInReview(now, pageable);
                default -> repository.findByStatus(status, pageable);
            };
        }

        String search = term.get();
        if (status == null) return repository.searchByTitle(search, pageable);
        return switch (status) {
            case OPEN -> repository.searchEffectivelyOpen(search, now, pageable);
            case IN_REVIEW -> repository.searchEffectivelyInReview(search, now, pageable);
            default -> repository.searchByStatusAndTitle(status, search, pageable);
        };
    }

    @Transactional(readOnly = true)
    public SelectionProcessDetailResponse findById(UUID id) {
        SelectionProcess process = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
        Map<UUID, ApplicationCounts.ApplicationStats> stats = applicationCounts.statsByProcess(List.of(id));
        return SelectionProcessDetailResponse.from(process, statsOf(stats, process), clock.instant());
    }

    private static ApplicationCounts.ApplicationStats statsOf(
            Map<UUID, ApplicationCounts.ApplicationStats> stats, SelectionProcess process) {
        return stats.getOrDefault(process.getId(), ApplicationCounts.ApplicationStats.NONE);
    }

    @Transactional(readOnly = true)
    List<PublicProcessResponse> listOpen() {
        Instant now = clock.instant();
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
                .map(process -> PublicProcessResponse.from(process, clock.instant()))
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    /** Único ponto que decide se uma inscrição entra — por isso a janela é checada aqui, não no status. */
    @Override
    @Transactional(readOnly = true)
    public Optional<SelectionProcess> findOpen(UUID processId) {
        Instant now = clock.instant();
        return repository.findByIdAndStatus(processId, SelectionProcessStatus.OPEN)
                .filter(process -> process.isAcceptingApplications(now));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID processId) {
        return repository.existsById(processId);
    }
}
