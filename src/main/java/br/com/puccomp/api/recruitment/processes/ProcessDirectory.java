package br.com.puccomp.api.recruitment.processes;

import java.util.Optional;
import java.util.UUID;

public interface ProcessDirectory {

    Optional<SelectionProcess> findOpen(UUID processId);

    boolean exists(UUID processId);
}
