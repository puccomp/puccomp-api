package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.recruitment.applications.Application;
import br.com.puccomp.api.recruitment.applications.ApplicationRepository;
import br.com.puccomp.api.recruitment.applications.ApplicationResponse;
import br.com.puccomp.api.recruitment.applications.ApplicationStatus;
import br.com.puccomp.api.recruitment.applications.CreateApplicationRequest;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SelectionProcessService {

    private final SelectionProcessRepository repository;
    private final ApplicationRepository applicationRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    List<SelectionProcessResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(SelectionProcessResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    SelectionProcessResponse findById(UUID id) {
        return repository.findById(id)
                .map(SelectionProcessResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));
    }

    @Transactional
    SelectionProcessResponse create(SelectionProcessRequest request) {
        SelectionProcess process = SelectionProcess.builder()
                .title(request.title().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .status(SelectionProcessStatus.DRAFT)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        return SelectionProcessResponse.from(repository.save(process));
    }

    @Transactional
    SelectionProcessResponse update(UUID id, SelectionProcessRequest request) {
        SelectionProcess process = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));

        process.update(
                request.title().trim(),
                request.description() != null ? request.description().trim() : null,
                request.startDate(),
                request.endDate());

        return SelectionProcessResponse.from(process);
    }

    @Transactional
    SelectionProcessResponse changeStatus(UUID id, SelectionProcessStatus status) {
        SelectionProcess process = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));

        process.changeStatus(status);
        return SelectionProcessResponse.from(process);
    }

    ApplicationResponse submitApplications(UUID processId, CreateApplicationRequest request) {
        SelectionProcess process = repository.findByIdWithoutTenant(processId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));

        if (process.getStatus() != SelectionProcessStatus.OPEN) {
            throw new IllegalArgumentException("Este processo seletivo não está aceitando candidaturas no momento");
        }

        TenantContext.set(process.getTenantId());

        return transactionTemplate.execute(status -> {
            if (applicationRepository.existsBySelectionProcessIdAndEmailIgnoreCase(processId, request.email())) {
                throw new ConflictException("Você já enviou uma candidatura para este processo seletivo");
            }

            Application application = Application.builder()
                    .tenantId(process.getTenantId())
                    .selectionProcess(process)
                    .fullName(request.fullName())
                    .email(request.email())
                    .phone(request.phone())
                    .university(request.university())
                    .course(request.course())
                    .currentTerm(request.currentTerm())
                    .linkedinUrl(request.linkedinUrl())
                    .portfolioUrl(request.portfolioUrl())
                    .status(ApplicationStatus.SUBMITTED)
                    .privacyConsent(request.privacyConsent())
                    .build();

            return ApplicationResponse.from(applicationRepository.save(application));
        });
    }

    @Transactional(readOnly = true)
    Page<ApplicationResponse> listApplications(UUID processId, Pageable pageable) {
        repository.findById(processId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo seletivo não encontrado"));

        return applicationRepository.findBySelectionProcessId(processId, pageable)
                .map(ApplicationResponse::from);
    }
}
