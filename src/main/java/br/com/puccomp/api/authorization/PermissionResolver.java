package br.com.puccomp.api.authorization;

import java.util.Set;
import java.util.UUID;


public interface PermissionResolver {

    Set<String> resolveAuthorities(UUID memberId, UUID roleId);

    Set<String> allAuthorities();

    Set<String> readOnlyAuthorities();
}
