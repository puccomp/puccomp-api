package br.com.puccomp.api.organization.members.history;

import java.time.Instant;

/**
 * Um intervalo em que o membro esteve ativo.
 *
 * @param startKnown false quando o intervalo já estava em curso no baseline: a pessoa estava ativa,
 *                   mas há quanto tempo ninguém sabe — e é isso que o exclui da permanência, em vez
 *                   de contá-lo a partir da migration
 * @param admission  true no primeiro intervalo de quem foi criado sob rastreamento; é o que o torna
 *                   uma admissão, e não uma reativação
 */
record ActivityInterval(Instant start, Instant end, boolean startKnown, boolean admission) {

    ActivityInterval closedAt(Instant at) {
        return new ActivityInterval(start, at, startKnown, admission);
    }

    /** Ativo em {@code t} menos um instante: quem entrou exatamente em t ainda não conta. */
    boolean activeJustBefore(Instant instant) {
        return start.isBefore(instant) && (end == null || !end.isBefore(instant));
    }

    /** Segundos de sobreposição com a janela, de {@code from} inclusive a {@code to} exclusivo. */
    long overlapSeconds(Instant from, Instant to) {
        Instant begin = start.isAfter(from) ? start : from;
        Instant finish = end == null || end.isAfter(to) ? to : end;
        return finish.isAfter(begin) ? java.time.Duration.between(begin, finish).getSeconds() : 0;
    }
}
