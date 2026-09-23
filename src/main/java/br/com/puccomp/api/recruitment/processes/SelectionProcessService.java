package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.recruitment.SelectionProcessPhaseChanged;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SelectionProcessService {

    private final SelectionProcessRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    SelectionProcessResponse create(SelectionProcessRequest request) {
        SelectionProcess process = SelectionProcess.builder()
                .title(request.title().trim())
                .status(SelectionProcessStatus.DRAFT)
                .build();
        apply(process, request);
        return SelectionProcessResponse.from(repository.save(process), clock.instant());
    }

    @Transactional
    SelectionProcessResponse update(UUID id, SelectionProcessRequest request) {
        SelectionProcess process = findOwned(id);
        apply(process, request);
        return SelectionProcessResponse.from(process, clock.instant());
    }

    /**
     * Publicado dentro da transação: o fato e o aviso pendente são gravados juntos, ou nenhum. E só
     * quando o status gravado mudou — repetir a requisição não pode reenviar o aviso a cada candidato.
     */
    @Transactional
    SelectionProcessResponse changeStatus(UUID id, SelectionProcessStatus status) {
        Instant now = clock.instant();
        SelectionProcess process = findOwned(id);
        if (process.changeStatusTo(status, now))
            phaseOf(status).ifPresent(phase -> events.publishEvent(new SelectionProcessPhaseChanged(
                    process.getTenantId(), process.getId(), process.getTitle(), phase,
                    process.getResultAt(), now)));
        return SelectionProcessResponse.from(process, now);
    }

    /** Rascunho não é fase para ninguém de fora: nada foi publicado, nada mudou para o candidato. */
    private static Optional<SelectionProcessPhaseChanged.Phase> phaseOf(SelectionProcessStatus status) {
        return Optional.ofNullable(switch (status) {
            case OPEN -> SelectionProcessPhaseChanged.Phase.OPENED;
            case IN_REVIEW -> SelectionProcessPhaseChanged.Phase.IN_REVIEW;
            case CLOSED -> SelectionProcessPhaseChanged.Phase.CLOSED;
            case CANCELLED -> SelectionProcessPhaseChanged.Phase.CANCELLED;
            case DRAFT -> null;
        });
    }

    private static void apply(SelectionProcess process, SelectionProcessRequest request) {
        process.update(request.title().trim(), trimmed(request.description()),
                request.opensAt(), request.closesAt(), request.resultAt(),
                request.minTerm(), request.maxTerm());
    }

    private SelectionProcess findOwned(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
