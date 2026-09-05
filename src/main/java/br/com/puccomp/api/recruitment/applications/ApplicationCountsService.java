package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.recruitment.processes.ApplicationCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ApplicationCountsService implements ApplicationCounts {

    private final CandidateApplicationRepository applications;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ApplicationStats> statsByProcess(Collection<UUID> processIds) {
        if (processIds.isEmpty()) return Map.of();
        return applications.aggregateByProcessIds(processIds).stream()
                .collect(Collectors.toMap(
                        CandidateApplicationRepository.ProcessStatsRow::getProcessId,
                        row -> new ApplicationStats(row.getTotal(), row.getLastSubmittedAt()),
                        (a, b) -> a));
    }
}
