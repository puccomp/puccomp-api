package br.com.puccomp.api.authorization.grant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissions;
    @Mock
    private MemberPermissionRepository memberPermissions;
    @InjectMocks
    private PermissionService service;

    private RolePermission rolePerm(UUID roleId, Permission p) {
        return RolePermission.builder().roleId(roleId).permission(p).build();
    }

    private MemberPermission memberPerm(UUID memberId, Permission p) {
        return MemberPermission.builder().memberId(memberId).permission(p).build();
    }

    @Test
    @DisplayName("permissões efetivas = união do cargo com os grants do membro")
    void shouldUnionCargoAndMemberGrants() {
        UUID member = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        when(rolePermissions.findByRoleId(cargo)).thenReturn(List.of(
                rolePerm(cargo, Permission.ROLES_READ), rolePerm(cargo, Permission.ROLES_WRITE)));
        when(memberPermissions.findByMemberId(member)).thenReturn(List.of(
                memberPerm(member, Permission.MEMBERS_READ)));

        Set<String> authorities = service.resolveAuthorities(member, cargo);

        assertThat(authorities).containsExactlyInAnyOrder("roles:read", "roles:write", "members:read");
    }

    @Test
    @DisplayName("sem cargo (null), só os grants do membro valem")
    void shouldResolveOnlyMemberGrantsWhenNoCargo() {
        UUID member = UUID.randomUUID();
        when(memberPermissions.findByMemberId(member)).thenReturn(List.of(
                memberPerm(member, Permission.ROLES_READ)));

        Set<String> authorities = service.resolveAuthorities(member, null);

        assertThat(authorities).containsExactly("roles:read");
    }

    @Test
    @DisplayName("grant do membro que coincide com o do cargo não duplica")
    void shouldDeduplicateOverlap() {
        UUID member = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        when(rolePermissions.findByRoleId(cargo)).thenReturn(List.of(rolePerm(cargo, Permission.ROLES_READ)));
        when(memberPermissions.findByMemberId(member)).thenReturn(List.of(memberPerm(member, Permission.ROLES_READ)));

        assertThat(service.resolveAuthorities(member, cargo)).containsExactly("roles:read");
    }

    @Test
    @DisplayName("allAuthorities devolve o catálogo inteiro")
    void shouldReturnFullCatalog() {
        assertThat(service.allAuthorities())
                .containsExactlyInAnyOrder("members:read", "members:write", "members:invite",
                        "roles:read", "roles:write", "departments:read", "departments:write", "courses:write",
                        "recruitment:read", "recruitment:write", "financial:read", "financial:write",
                        "permissions:manage");
    }
}
