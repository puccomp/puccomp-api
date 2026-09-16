package br.com.puccomp.api.recruitment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Leitura das inscrições para quem avisa. É o que um evento não carrega: a lista de candidatos tem
 * centenas de linhas e não cabe num payload de outbox.
 */
public interface CandidateDirectory {

    /** Candidatos de um processo, um por e-mail distinto: quem se inscreveu uma vez recebe uma vez. */
    List<Candidate> candidatesOf(UUID tenantId, UUID processId);

    /** Inscrições recebidas desde o instante, agrupadas por EJ. Cruza tenants: é rotina de manutenção. */
    List<TenantArrivals> arrivalsSince(Instant from);

    record Candidate(String name, String email) { }

    record TenantArrivals(UUID tenantId, long total, List<ProcessArrivals> byProcess) { }

    record ProcessArrivals(UUID processId, String title, long total) { }
}
