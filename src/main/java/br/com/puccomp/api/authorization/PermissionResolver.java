package br.com.puccomp.api.authorization;

import br.com.puccomp.api.shared.reference.Standing;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Traduz um membro nas permissões que ele de fato tem. A precedência — alumni só lê, dono pode tudo,
 * o resto é a união do cargo com os grants individuais — mora só aqui: nenhum chamador a reimplementa.
 */
public interface PermissionResolver {

    Set<String> effectiveAuthorities(Subject subject);

    /** Resolve o lote inteiro em duas consultas, independente de quantos membros a EJ tenha. */
    Set<UUID> filterWithPermission(Collection<Subject> subjects, String permission);

    /**
     * Todos os códigos que o sistema reconhece. Quem recebe código de permissão do usuário valida
     * contra isto em vez de manter a própria lista — o catálogo mora só neste módulo.
     */
    Set<String> catalog();

    record Subject(UUID memberId, UUID roleId, Standing standing, boolean readOnly) { }
}
