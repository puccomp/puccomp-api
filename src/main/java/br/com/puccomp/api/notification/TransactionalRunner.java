package br.com.puccomp.api.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Abre uma transação nova para o bloco. Existe para {@link TenantScope} controlar o momento disso. */
@Component
class TransactionalRunner {

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void run(Runnable work) {
        work.run();
    }
}
