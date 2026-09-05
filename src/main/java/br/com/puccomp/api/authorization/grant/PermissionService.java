package br.com.puccomp.api.authorization.grant;

import br.com.puccomp.api.authorization.PermissionResolver;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class PermissionService implements PermissionResolver {

    private final RolePermissionRepository rolePermissions;
    private final MemberPermissionRepository memberPermissions;

    @Override
    @Transactional(readOnly = true)
    public Set<String> effectiveAuthorities(Subject subject) {
        if (subject.readOnly()) return readOnlyAuthorities();
        if (subject.standing() == Standing.OWNER) return allAuthorities();
        return resolveAuthorities(subject.memberId(), subject.roleId());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> filterWithPermission(Collection<Subject> subjects, String permission) {
        Optional<Permission> required = Permission.fromCode(permission);
        if (subjects.isEmpty() || required.isEmpty()) return Set.of();

        Set<UUID> roleIds = idsOf(subjects, Subject::roleId);
        Set<UUID> memberIds = idsOf(subjects, Subject::memberId);
        Set<UUID> grantedRoles = roleIds.isEmpty() ? Set.of()
                : rolePermissions.findByRoleIdInAndPermission(roleIds, required.get()).stream()
                        .map(RolePermission::getRoleId).collect(Collectors.toSet());
        Set<UUID> grantedMembers = memberIds.isEmpty() ? Set.of()
                : memberPermissions.findByMemberIdInAndPermission(memberIds, required.get()).stream()
                        .map(MemberPermission::getMemberId).collect(Collectors.toSet());

        return subjects.stream()
                .filter(subject -> holds(subject, required.get(), grantedRoles, grantedMembers))
                .map(Subject::memberId)
                .collect(Collectors.toSet());
    }

    private boolean holds(Subject subject, Permission required, Set<UUID> grantedRoles, Set<UUID> grantedMembers) {
        if (subject.readOnly()) return readOnlyAuthorities().contains(required.code());
        if (subject.standing() == Standing.OWNER) return true;
        return (subject.roleId() != null && grantedRoles.contains(subject.roleId()))
                || (subject.memberId() != null && grantedMembers.contains(subject.memberId()));
    }

    private static Set<UUID> idsOf(Collection<Subject> subjects, Function<Subject, UUID> id) {
        return subjects.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    Set<String> resolveAuthorities(UUID memberId, UUID roleId) {
        Set<String> authorities = new HashSet<>();
        if (roleId != null)
            rolePermissions.findByRoleId(roleId)
                    .forEach(rp -> authorities.add(rp.getPermission().code()));

        if (memberId != null)
            memberPermissions.findByMemberId(memberId)
                    .forEach(mp -> authorities.add(mp.getPermission().code()));
        return authorities;
    }

    Set<String> allAuthorities() {
        return Arrays.stream(Permission.values()).map(Permission::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<String> readOnlyAuthorities() {
        return Arrays.stream(Permission.values()).map(Permission::code)
                .filter(code -> code.endsWith(":read"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
