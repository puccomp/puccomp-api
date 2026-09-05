package br.com.puccomp.api.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
class SmtpMailer implements Mailer {

    private final AsyncMailDeliverer deliverer;

    @Override
    public void send(EmailMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliverer.deliver(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliverer.deliver(message);
            }
        });
    }
}
