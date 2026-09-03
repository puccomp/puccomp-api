package br.com.puccomp.api.recruitment.processes;

import java.util.UUID;

public record PublicProcessResponse(
        UUID id,
        String title,
        String description
) {
    static PublicProcessResponse from(SelectionProcess process) {
        return new PublicProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription());
    }
}
