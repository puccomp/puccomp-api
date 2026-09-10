package br.com.puccomp.api.organization.members.history;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O vínculo de um membro visto como intervalos de atividade.
 *
 * @param baselineAt nulo se o membro nasceu sob rastreamento. Membro de baseline não recebe coorte
 *                   inventada nem data de entrada presumida.
 */
record MemberHistory(UUID memberId, Instant baselineAt, List<ActivityInterval> intervals) {

    boolean fromBaseline() {
        return baselineAt != null;
    }

    Optional<ActivityInterval> admission() {
        return intervals.stream().filter(interval -> interval.admission()).findFirst();
    }

    boolean activeJustBefore(Instant instant) {
        return intervals.stream().anyMatch(interval -> interval.activeJustBefore(instant));
    }
}
