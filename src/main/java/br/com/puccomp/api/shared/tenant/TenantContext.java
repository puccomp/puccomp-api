package br.com.puccomp.api.shared.tenant;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Roda o bloco no tenant informado e devolve o anterior — inclusive nenhum. É para trabalho
     * fora de uma requisição, onde o {@code ThreadLocal} do filtro não chegou.
     */
    public static void runIn(UUID tenantId, Runnable work) {
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            work.run();
        } finally {
            if (previous == null) clear();
            else set(previous);
        }
    }
}
