package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.organization.members.MemberStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reconstrói, a partir dos eventos, os intervalos ativos de cada membro.
 *
 * <p>Nada aqui olha para {@code Member.status}: o estado de hoje não sabe dizer quem estava ativo
 * em março, e usá-lo para rotular o passado é justamente o erro que o histórico existe para evitar.
 */
final class MembershipTimeline {

    private MembershipTimeline() { }

    /** Os eventos precisam vir ordenados por membro e depois por sequência. */
    static List<MemberHistory> from(List<MemberStatusEvent> orderedEvents) {
        List<MemberHistory> histories = new ArrayList<>();
        State state = null;

        for (MemberStatusEvent event : orderedEvents) {
            if (state == null || !event.getMemberId().equals(state.memberId)) {
                if (state != null) histories.add(state.toHistory());
                state = new State(event.getMemberId());
            }
            state.apply(event);
        }
        if (state != null) histories.add(state.toHistory());
        return histories;
    }

    private static final class State {

        private final UUID memberId;
        private final List<ActivityInterval> intervals = new ArrayList<>();
        private Instant baselineAt;
        private ActivityInterval open;
        private boolean createdUnderTracking;
        private boolean hadActivation;

        private State(UUID memberId) {
            this.memberId = memberId;
        }

        private void apply(MemberStatusEvent event) {
            boolean wasActive = open != null;
            boolean isActive = event.getToStatus() == MemberStatus.ACTIVE;

            switch (event.getKind()) {
                case BASELINE -> {
                    baselineAt = event.getOccurredAt();
                    // Já estava ativo no marco: o intervalo existe, mas seu início é desconhecido.
                    if (isActive) open = new ActivityInterval(event.getOccurredAt(), null, false, false);
                }
                case CREATED -> {
                    createdUnderTracking = true;
                    if (isActive) open = activation(event.getOccurredAt());
                }
                case STATUS_CHANGED -> {
                    if (isActive && !wasActive) {
                        open = activation(event.getOccurredAt());
                    } else if (!isActive && wasActive) {
                        // Sair de ACTIVE encerra o intervalo. Trocar ALUMNUS por INACTIVE não passa
                        // por aqui: quem já não estava ativo não sai uma segunda vez.
                        intervals.add(open.closedAt(event.getOccurredAt()));
                        open = null;
                    }
                }
            }
        }

        /**
         * Primeira ativação de quem foi criado sob rastreamento é admissão; qualquer outra é
         * reativação. Para membro de baseline, o retorno à atividade é reativação por convenção —
         * presumir que aquela foi a sua primeira entrada inventaria uma admissão que ninguém viu.
         */
        private ActivityInterval activation(Instant at) {
            boolean admission = createdUnderTracking && !hadActivation;
            hadActivation = true;
            return new ActivityInterval(at, null, true, admission);
        }

        private MemberHistory toHistory() {
            List<ActivityInterval> all = new ArrayList<>(intervals);
            if (open != null) all.add(open);
            all.sort(java.util.Comparator.comparing(interval -> interval.start()));
            return new MemberHistory(memberId, baselineAt, List.copyOf(all));
        }
    }
}
