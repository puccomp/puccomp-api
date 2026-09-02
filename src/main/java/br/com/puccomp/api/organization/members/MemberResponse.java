package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.shared.reference.Standing;

import java.util.UUID;

public record MemberResponse(UUID id, String name, MemberStatus status, Standing standing, NamedRef course,
                             NamedRef role, NamedRef department) {

    static MemberResponse from(Member member) {
        var role = member.getRole();
        var department = member.getDepartment();
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getStatus(),
                member.getStanding(),
                NamedRef.of(member.getCourse().getId(), member.getCourse().getName()),
                role != null ? NamedRef.of(role.getId(), role.getName()) : null,
                department != null ? NamedRef.of(department.getId(), department.getName()) : null
        );
    }
}
