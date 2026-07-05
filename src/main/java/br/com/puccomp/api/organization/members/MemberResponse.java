package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.Course;
import br.com.puccomp.api.shared.reference.Standing;

import java.util.UUID;

public record MemberResponse(UUID id, String name, MemberStatus status, Standing standing, Course course,
                             String role, String department) {

    static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getStatus(),
                member.getStanding(),
                member.getCourse(),
                member.getRole() != null ? member.getRole().getName() : null,
                member.getDepartment() != null ? member.getDepartment().getName() : null
        );
    }
}
