package br.com.puccomp.api.authorization.grant;

import br.com.puccomp.api.authorization.PermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
class PermissionService implements PermissionResolver {

    private final RolePermissionRepository rolePermissions;
    private final MemberPermissionRepository memberPermissions;

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolveAuthorities(UUID memberId, UUID roleId) {
        Set<String> authorities = new HashSet<>();
        if (roleId != null)
            rolePermissions.findByRoleId(roleId)
                    .forEach(rp -> authorities.add(rp.getPermission().code()));

        if (memberId != null)
            memberPermissions.findByMemberId(memberId)
                    .forEach(mp -> authorities.add(mp.getPermission().code()));
        return authorities;
    }

    @Override
    public Set<String> allAuthorities() {
        return Arrays.stream(Permission.values()).map(Permission::code)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<String> readOnlyAuthorities() {
        return Arrays.stream(Permission.values()).map(Permission::code)
                .filter(code -> code.endsWith(":read"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    List<String> getRolePermissions(UUID roleId) {
        return codesSorted(rolePermissions.findByRoleId(roleId).stream()
                .map(RolePermission::getPermission));
    }

    @Transactional
    void setRolePermissions(UUID roleId, Set<Permission> permissions) {
        rolePermissions.deleteAll(rolePermissions.findByRoleId(roleId));
        rolePermissions.flush();
        permissions.forEach(p -> rolePermissions.save(
                RolePermission.builder().roleId(roleId).permission(p).build()));
    }

    @Transactional(readOnly = true)
    List<String> getMemberPermissions(UUID memberId) {
        return codesSorted(memberPermissions.findByMemberId(memberId).stream()
                .map(MemberPermission::getPermission));
    }

    @Transactional
    void setMemberPermissions(UUID memberId, Set<Permission> permissions) {
        memberPermissions.deleteAll(memberPermissions.findByMemberId(memberId));
        memberPermissions.flush();
        permissions.forEach(p -> memberPermissions.save(
                MemberPermission.builder().memberId(memberId).permission(p).build()));
    }

    private static List<String> codesSorted(java.util.stream.Stream<Permission> permissions) {
        return permissions.map(Permission::code).sorted().toList();
    }
}
