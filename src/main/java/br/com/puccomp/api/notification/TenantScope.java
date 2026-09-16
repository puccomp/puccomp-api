package br.com.puccomp.api.notification;

import br.com.puccomp.api.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Roda o aviso dentro da EJ a que o fato pertence — escopo primeiro, transação depois.
 *
 * <p>A ordem é o ponto: o Hibernate resolve o tenant <b>quando a sessão abre</b>, então abrir o
 * escopo dentro de um método já transacional chega tarde, e toda consulta dali em diante não
 * enxerga linha nenhuma, em silêncio.
 *
 * <p>O tenant vem do evento e não da thread: o listener é assíncrono, e a republicação no reinício
 * não tem requisição de onde herdá-lo.
 */
@Component
@RequiredArgsConstructor
class TenantScope {

    private final TransactionalRunner runner;

    void run(UUID tenantId, Runnable work) {
        TenantContext.runIn(tenantId, () -> runner.run(work));
    }
}
