package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SelectionProcessService implements ProcessDirectory {

    private final SelectionProcessRepository repository;

    @Transactional(readOnly = true)
    List<SelectionProcessResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(SelectionProcessResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    SelectionProcessResponse findById(UUID id) {
        return SelectionProcessResponse.from(findOwned(id));
    }

    @Transactional
    SelectionProcessResponse create(SelectionProcessRequest request) {
        SelectionProcess process = SelectionProcess.builder()
                .title(request.title().trim())
                .description(trimmed(request.description()))
                .status(SelectionProcessStatus.DRAFT)
                .opensAt(request.opensAt())
                .closesAt(request.closesAt())
                .build();

        return SelectionProcessResponse.from(repository.save(process));
    }

    @Transactional
    SelectionProcessResponse update(UUID id, SelectionProcessRequest request) {
        SelectionProcess process = findOwned(id);
        process.update(
                request.title().trim(),
                trimmed(request.description()),
                request.opensAt(),
                request.closesAt());

        return SelectionProcessResponse.from(process);
    }

    @Transactional
    SelectionProcessResponse changeStatus(UUID id, SelectionProcessStatus status) {
        SelectionProcess process = findOwned(id);
        process.changeStatusTo(status);
        return SelectionProcessResponse.from(process);
    }

    @Transactional(readOnly = true)
    List<PublicProcessResponse> listOpen() {
        return repository.findByStatusOrderByCreatedAtDesc(SelectionProcessStatus.OPEN).stream()
                .map(PublicProcessResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    PublicProcessResponse findOpenById(UUID id) {
        return findOpen(id)
                .map(PublicProcessResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SelectionProcess> findOpen(UUID processId) {
        return repository.findByIdAndStatus(processId, SelectionProcessStatus.OPEN);
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
