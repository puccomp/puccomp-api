package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SelectionProcessService implements ProcessDirectory {

    private final SelectionProcessRepository repository;

    @Transactional(readOnly = true)
    List<SelectionProcessResponse> findAll() {
        Instant now = Instant.now();
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(process -> SelectionProcessResponse.from(process, now))
                .toList();
    }

    @Transactional(readOnly = true)
    SelectionProcessResponse findById(UUID id) {
        return SelectionProcessResponse.from(findOwned(id), Instant.now());
    }

    @Transactional
    SelectionProcessResponse create(SelectionProcessRequest request) {
        SelectionProcess process = SelectionProcess.builder()
                .title(request.title().trim())
                .status(SelectionProcessStatus.DRAFT)
                .build();
        process.update(request.title().trim(), trimmed(request.description()),
                request.opensAt(), request.closesAt(), request.resultAt());

        return SelectionProcessResponse.from(repository.save(process), Instant.now());
    }

    @Transactional
    SelectionProcessResponse update(UUID id, SelectionProcessRequest request) {
        SelectionProcess process = findOwned(id);
        process.update(request.title().trim(), trimmed(request.description()),
                request.opensAt(), request.closesAt(), request.resultAt());

        return SelectionProcessResponse.from(process, Instant.now());
    }

    @Transactional
    SelectionProcessResponse changeStatus(UUID id, SelectionProcessStatus status) {
        Instant now = Instant.now();
        SelectionProcess process = findOwned(id);
        process.changeStatusTo(status, now);
        return SelectionProcessResponse.from(process, now);
    }

    @Transactional(readOnly = true)
    List<PublicProcessResponse> listOpen() {
        Instant now = Instant.now();
        return repository.findByStatusOrderByCreatedAtDesc(SelectionProcessStatus.OPEN).stream()
                .filter(process -> process.isAcceptingApplications(now))
                .map(PublicProcessResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    PublicProcessResponse findOpenById(UUID id) {
        return findOpen(id)
                .map(PublicProcessResponse::from)
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

    private SelectionProcess findOwned(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
