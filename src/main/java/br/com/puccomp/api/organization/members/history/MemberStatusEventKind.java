package br.com.puccomp.api.organization.members.history;

/** Por que um estado foi registrado. Distinguir observação de transição é o ponto do histórico. */
enum MemberStatusEventKind {

    /** Estado em que o membro foi encontrado quando o rastreamento começou: nem entrada, nem saída. */
    BASELINE,

    CREATED,

    STATUS_CHANGED,

    /**
     * Saiu da EJ. É uma saída de verdade — encerra o intervalo ativo e conta no turnover —, mesmo
     * que a projeção {@code Member.status} não mude: quem some do contrato some estando ativo.
     */
    DELETED,

    /** Voltou depois de uma deleção. Reativação, nunca admissão: a entrada original não se repete. */
    RESTORED
}
