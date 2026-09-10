package br.com.puccomp.api.shared.aggregation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Categoria de uma distribuição: identificador estável e rótulo de exibição.
 *
 * <p>Não substitui {@link br.com.puccomp.api.shared.reference.NamedRef}: aquele referencia um
 * recurso e seu {@code id} é sempre um UUID. Aqui o identificador é textual porque uma distribuição
 * também agrupa por enum ({@code ACTIVE}) e por número ({@code 3}), e a ausência de vínculo é uma
 * categoria legítima com {@code id} nulo.
 */
@Schema(description = "Categoria de uma distribuição. id nulo é a categoria sem vínculo; "
        + "navegá-la exige o filtro de presença documentado no endpoint, nunca a string \"null\".")
public record CategoryKey(
        @Schema(description = "Identificador estável, independente do rótulo: UUID textual, "
                + "valor do enum em inglês ou número decimal. Nulo na categoria sem vínculo",
                example = "ACTIVE")
        String id,

        @Schema(description = "Rótulo de exibição, em português", example = "Ativo")
        String name
) {

    public static CategoryKey of(UUID id, String name) {
        return new CategoryKey(id == null ? null : id.toString(), name);
    }

    public static CategoryKey of(Enum<?> value, String name) {
        return new CategoryKey(value == null ? null : value.name(), name);
    }

    public static CategoryKey of(Number id, String name) {
        return new CategoryKey(id == null ? null : id.toString(), name);
    }

    /** Categoria de quem não tem o vínculo agrupado — cargo, diretoria, período não informado. */
    public static CategoryKey absent(String name) {
        return new CategoryKey(null, name);
    }
}
