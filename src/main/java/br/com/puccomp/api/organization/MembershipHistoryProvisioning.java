package br.com.puccomp.api.organization;

/** Vitrine para o provisionamento de EJ marcar o início da cobertura do histórico de vínculos. */
public interface MembershipHistoryProvisioning {

    /**
     * Inicia o rastreamento da EJ corrente, se ainda não houver marco. Na mesma transação da
     * criação do tenant: EJ nova nasce coberta, mesmo sem nenhum membro ainda.
     */
    void startTracking();
}
