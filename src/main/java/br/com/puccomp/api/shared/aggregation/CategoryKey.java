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
@Schema(description = "Categoria de uma distribuição. Use kind para saber qual das três ela é: "
        + "id nulo sozinho não distingue a ausência de vínculo da cauda agregada.")
public record CategoryKey(
        @Schema(description = "Identificador estável, independente do rótulo: UUID textual, "
                + "valor do enum em inglês ou número decimal. Nulo na categoria sem vínculo",
                example = "ACTIVE")
        String id,

        @Schema(description = "Rótulo de exibição, em português", example = "Ativo")
        String name,

        @Schema(description = "O que a categoria é. Sempre presente: ITEM é uma categoria real; "
                + "ABSENT é quem não tem o vínculo, e pede atenção; OTHERS é a cauda agregada "
                + "por slice_limit, e é só resto. Os dois últimos têm id nulo, e sem este campo "
                + "seriam indistinguíveis a não ser pelo rótulo — que é texto de exibição, não "
                + "contrato", example = "ITEM")
        Kind kind
) {

    public enum Kind { ITEM, ABSENT, OTHERS }

    public static CategoryKey of(UUID id, String name) {
        return identified(id == null ? null : id.toString(), name);
    }

    public static CategoryKey of(Enum<?> value, String name) {
        return identified(value == null ? null : value.name(), name);
    }

    public static CategoryKey of(Number id, String name) {
        return identified(id == null ? null : id.toString(), name);
    }

    /** Categoria que é texto livre: identificador e rótulo coincidem por não haver catálogo. */
    public static CategoryKey of(String value) {
        return identified(value, value);
    }

    /** Categoria de quem não tem o vínculo agrupado — cargo, diretoria, período não informado. */
    public static CategoryKey absent(String name) {
        return new CategoryKey(null, name, Kind.ABSENT);
    }

    /** Cauda agregada por slice_limit: resto, não uma categoria que exista na EJ. */
    public static CategoryKey others(String name) {
        return new CategoryKey(null, name, Kind.OTHERS);
    }

    /** Sem identificador não há categoria real, e a única leitura honesta é ausência de vínculo. */
    private static CategoryKey identified(String id, String name) {
        return id == null ? new CategoryKey(null, name, Kind.ABSENT)
                : new CategoryKey(id, name, Kind.ITEM);
    }
}
