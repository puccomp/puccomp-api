package br.com.puccomp.api.recruitment.processes;

import java.util.Optional;
import java.util.UUID;

/**
 * O que {@code candidacies} pode fazer com um processo seletivo. Ler processos abertos e conferir
 * existência — nada de escrita, nada de rascunho.
 */
public interface ProcessDirectory {

    Optional<SelectionProcess> findOpen(UUID processId);

    boolean exists(UUID processId);
}
