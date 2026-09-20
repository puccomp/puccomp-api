package br.com.puccomp.api.organization;

import br.com.puccomp.api.shared.reference.Standing;

import java.util.UUID;

public interface MemberProvisioning {

    boolean roleExists(UUID roleId);

    /**
     * O e-mail vem de quem provisiona — identity, que já o tem na mão — em vez de organization ir
     * buscá-lo: o caminho inverso fecharia um ciclo entre os dois módulos.
     */
    UUID createMember(UUID accountId, String name, String email, UUID courseId, UUID roleId,
                      Standing standing);
}
