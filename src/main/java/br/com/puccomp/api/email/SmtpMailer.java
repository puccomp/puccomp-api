package br.com.puccomp.api.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
class SmtpMailer implements Mailer {

    private final AsyncMailDeliverer deliverer;

    @Override
    public void send(EmailMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliverer.enqueue(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliverer.enqueue(message);
            }
        });
    }

    @Override
    public void deliver(EmailMessage message) {
        deliverer.deliver(message);
    }
}
