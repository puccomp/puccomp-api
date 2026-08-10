package br.com.puccomp.api.recruitment.processes;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum SelectionProcessStatus {

    DRAFT,

    OPEN,

    CLOSED,

    FINISHED,

    CANCELLED;

    private static final Map<SelectionProcessStatus, Set<SelectionProcessStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(OPEN, CANCELLED),
            OPEN, EnumSet.of(CLOSED, CANCELLED),
            CLOSED, EnumSet.of(FINISHED, CANCELLED),
            FINISHED, EnumSet.noneOf(SelectionProcessStatus.class),
            CANCELLED, EnumSet.noneOf(SelectionProcessStatus.class));

    boolean canTransitionTo(SelectionProcessStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
