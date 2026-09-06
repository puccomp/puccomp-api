package br.com.puccomp.api.shared.reference;

import java.util.UUID;

/**
 * Referência a outro recurso, com o nome junto para o cliente exibir sem uma segunda chamada.
 *
 * <p>Existe porque devolver só o nome ({@code "department": "Comercial"}) deixa o cliente sem como
 * navegar ou filtrar: os filtros da API são por id.
 */
public record NamedRef(UUID id, String name) {

    public static NamedRef of(UUID id, String name) {
        return id == null ? null : new NamedRef(id, name);
    }
}
