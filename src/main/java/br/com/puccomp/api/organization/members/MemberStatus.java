package br.com.puccomp.api.organization.members;

/**
 * Os dois estados de um vínculo que existe. Sair da EJ não é um terceiro estado: é deleção, e ela
 * mora em {@code Member.deletedAt} — ver {@code MemberService.delete}.
 */
public enum MemberStatus {

    /** No quadro, com o acesso que o cargo concede. */
    ACTIVE,

    /** Encerrou o ciclo ativo e continua lendo a EJ; perde a escrita. */
    ALUMNUS
}
