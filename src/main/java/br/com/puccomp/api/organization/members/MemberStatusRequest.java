package br.com.puccomp.api.organization.members;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * O estado alvo do vínculo. Um PUT em vez de duas rotas de ação porque a transição é idempotente:
 * aposentar quem já é alumnus não é um segundo desligamento.
 */
public record MemberStatusRequest(
        @NotNull
        @Schema(description = "ACTIVE devolve o quadro ativo; ALUMNUS encerra o ciclo e deixa o "
                + "acesso somente leitura", example = "ALUMNUS")
        MemberStatus value
) { }
