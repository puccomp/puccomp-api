package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.shared.reference.Standing;

import java.time.Instant;
import java.util.UUID;

/**
 * O membro como o cliente o vê. {@code accountId} não aparece de propósito: a API pública
 * identifica pessoa por {@code member_id}, e a conta é detalhe interno de {@code identity}.
 *
 * @param email    nulo em membro sem conta associada — linha de baseline ou de seed
 * @param joinedAt primeira ativação conhecida. Nulo em membro de baseline, o que significa "já
 *                 estava na EJ quando o rastreamento começou", nunca "entrou agora"
 */
public record MemberResponse(UUID id, String name, String email, MemberStatus status, Standing standing,
                             NamedRef course, NamedRef role, NamedRef department, Instant joinedAt) {

    static MemberResponse from(Member member, Instant joinedAt) {
        var role = member.getRole();
        var department = member.getDepartment();
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getStatus(),
                member.getStanding(),
                NamedRef.of(member.getCourse().getId(), member.getCourse().getName()),
                role != null ? NamedRef.of(role.getId(), role.getName()) : null,
                department != null ? NamedRef.of(department.getId(), department.getName()) : null,
                joinedAt
        );
    }
}
