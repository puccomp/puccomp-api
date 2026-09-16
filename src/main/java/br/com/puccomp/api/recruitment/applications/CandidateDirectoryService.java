package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.recruitment.CandidateDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leituras que servem aos avisos, em JDBC com o tenant explícito: o resumo diário cruza todas as
 * EJs, um recorte que o filtro de tenant recusa. Mesma escolha da limpeza de arquivos pendentes.
 */
@Service
@RequiredArgsConstructor
class CandidateDirectoryService implements CandidateDirectory {

    private final JdbcClient jdbc;

    @Override
    public List<Candidate> candidatesOf(UUID tenantId, UUID processId) {
        return jdbc.sql("""
                select min(full_name) as name, min(email) as email
                from candidate_applications
                where tenant_id = :tenant and process_id = :process
                group by lower(email)
                order by min(created_at)
                """)
                .param("tenant", tenantId)
                .param("process", processId)
                .query(Candidate.class)
                .list();
    }

    @Override
    public List<TenantArrivals> arrivalsSince(Instant from) {
        List<ArrivalRow> rows = jdbc.sql("""
                select a.tenant_id, a.process_id, p.title, count(*) as total
                from candidate_applications a
                join selection_processes p on p.tenant_id = a.tenant_id and p.id = a.process_id
                where a.created_at >= :from
                group by a.tenant_id, a.process_id, p.title
                order by a.tenant_id, count(*) desc, p.title
                """)
                .param("from", from)
                .query(ArrivalRow.class)
                .list();

        Map<UUID, List<ProcessArrivals>> byTenant = new LinkedHashMap<>();
        for (ArrivalRow row : rows)
            byTenant.computeIfAbsent(row.tenantId(), key -> new ArrayList<>())
                    .add(new ProcessArrivals(row.processId(), row.title(), row.total()));

        return byTenant.entrySet().stream()
                .map(entry -> new TenantArrivals(entry.getKey(),
                        entry.getValue().stream().mapToLong(process -> process.total()).sum(),
                        List.copyOf(entry.getValue())))
                .toList();
    }

    record ArrivalRow(UUID tenantId, UUID processId, String title, long total) { }
}
