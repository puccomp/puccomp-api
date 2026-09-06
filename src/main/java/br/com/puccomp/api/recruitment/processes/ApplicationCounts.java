package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * O que {@code processes} precisa saber sobre inscrições, declarado aqui e implementado por
 * {@code applications} — que é quem tem a tabela. Inverter a dependência mantém a seta apontando
 * num sentido só, e o lote evita uma consulta por processo na listagem.
 */
public interface ApplicationCounts {

    Map<UUID, ApplicationStats> statsByProcess(Collection<UUID> processIds);

    record ApplicationStats(long total, Instant lastSubmittedAt) {

        static final ApplicationStats NONE = new ApplicationStats(0, null);
    }
}
