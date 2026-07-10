package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.Standing;

import java.util.UUID;

public record MemberResponse(UUID id, String name, MemberStatus status, Standing standing, String course,
                             String role, String department) {

    static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getStatus(),
                member.getStanding(),
                member.getCourse().getName(),
                member.getRole() != null ? member.getRole().getName() : null,
                member.getDepartment() != null ? member.getDepartment().getName() : null
        );
    }
}
