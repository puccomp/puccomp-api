package br.com.puccomp.api.organization;

import java.time.Instant;
import java.util.UUID;

/** Um membro passou a ocupar (ou deixou de ocupar) um cargo e uma diretoria. */
public record MemberAssigned(
        UUID tenantId,
        UUID memberId,
        UUID accountId,
        String memberName,
        String roleName,
        String departmentName,
        Instant at
) { }
