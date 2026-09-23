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
record MemberHistory(UUID memberId, Instant baselineAt, Instant knownSince, Instant goneAt,
                     List<ActivityInterval> intervals) {

    boolean fromBaseline() {
        return baselineAt != null;
    }

    Optional<ActivityInterval> admission() {
        return intervals.stream().filter(interval -> interval.admission()).findFirst();
    }

    boolean activeJustBefore(Instant instant) {
        return intervals.stream().anyMatch(interval -> interval.activeJustBefore(instant));
    }

    /** O vínculo existia em {@code t} menos um instante: já observado e ainda não removido. */
    boolean existsJustBefore(Instant instant) {
        return knownSince != null && knownSince.isBefore(instant)
                && (goneAt == null || !goneAt.isBefore(instant));
    }

    /** Existia e não estava no quadro ativo. É o alumnus daquele mês, não o de hoje. */
    boolean alumnusJustBefore(Instant instant) {
        return existsJustBefore(instant) && !activeJustBefore(instant);
    }
}
