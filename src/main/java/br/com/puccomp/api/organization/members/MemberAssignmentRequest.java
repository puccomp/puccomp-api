package br.com.puccomp.api.organization.members;

import java.util.UUID;

public record MemberAssignmentRequest(UUID roleId, UUID departmentId) {
}
