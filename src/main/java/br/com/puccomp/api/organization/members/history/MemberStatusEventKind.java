package br.com.puccomp.api.organization.members.history;

/** Por que um estado foi registrado. Distinguir observação de transição é o ponto do histórico. */
enum MemberStatusEventKind {

    /** Estado em que o membro foi encontrado quando o rastreamento começou: nem entrada, nem saída. */
    BASELINE,

    CREATED,

    STATUS_CHANGED
}
