package br.com.puccomp.api.organization;

import br.com.puccomp.api.shared.reference.Standing;

import java.util.UUID;

public interface MemberProvisioning {

    boolean roleExists(UUID roleId);

    UUID createMember(UUID accountId, String name, UUID courseId, UUID roleId, Standing standing);
}
