package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.files.FileUpload;
import br.com.puccomp.api.recruitment.applications.summary.ApplicationHistorySummaryResponse;
import br.com.puccomp.api.recruitment.applications.summary.ApplicationSummaryResponse;
import br.com.puccomp.api.recruitment.applications.summary.ApplicationSummaryService;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateApplicationService {

    private final CandidateApplicationRegistry registry;
    private final ApplicationSummaryService summaries;
    private final FileService files;

    Page<SignedCandidateApplicationResponse> listByProcessSigned(UUID processId, CandidateApplicationFilter filter,
                                                                 Pageable pageable) {
        return registry.listByProcessSigned(processId, filter, pageable);
    }

    public Page<CandidateApplicationResponse> searchAcrossProcesses(CandidateApplicationFilter filter, Pageable pageable) {
        return registry.searchAcrossProcesses(filter, pageable);
    }

    Page<SignedCandidateApplicationResponse> searchAcrossProcessesSigned(CandidateApplicationFilter filter,
                                                                         Pageable pageable) {
        return registry.searchAcrossProcessesSigned(filter, pageable);
    }

    CandidateApplicationResponse findById(UUID applicationId) {
        return registry.findById(applicationId);
    }

    FileDownload cvOf(UUID applicationId) {
        return registry.cvOf(applicationId);
    }

    public ApplicationSummaryResponse summarize(UUID processId, CandidateApplicationFilter filter) {
        return summaries.summarize(processId, filter, OrganizationTime.ZONE);
    }

    public ApplicationHistorySummaryResponse summarizeHistory(CandidateApplicationFilter filter) {
        return summaries.summarizeHistory(filter, OrganizationTime.ZONE);
    }

    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request) {
        return submit(processId, request, null);
    }

    CandidateApplicationReceiptResponse submit(UUID processId, SubmitCandidateApplicationRequest request,
                                               FileUpload cv) {
        registry.requireSubmittable(processId, request);
        // Antivírus e S3 levam dezenas de segundos: rodam sem transação aberta, e o arquivo só
        // vira currículo válido no register. Reserva abandonada é recolhida pela limpeza.
        UUID cvFileId = cv == null ? null : files.stage(cv);
        return registry.register(processId, request, cvFileId);
    }
}
