package br.com.puccomp.api.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
class SmtpMailer implements Mailer {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    @Override
    public void send(EmailMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(message);
            }
        });
    }

    private void deliver(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(EmailTemplate.render(message.template(), message.variables()), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Falha ao enviar o template {} para {}: {}",
                    message.template(), message.to(), e.getMessage(), e);
        }
    }
}
