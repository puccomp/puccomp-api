package br.com.puccomp.api.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
class AsyncMailDeliverer {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    /** Entrega sem bloquear quem pediu. Falha aqui acaba no log: não há quem tente de novo. */
    @Async("mailTaskExecutor")
    void enqueue(EmailMessage message) {
        try {
            deliver(message);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar o template {} para {}: {}",
                    message.template(), message.to(), e.getMessage(), e);
        }
    }

    /** Entrega agora e deixa a falha subir, para quem sabe reprocessar. */
    void deliver(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(EmailTemplate.render(message.template(), message.subject(),
                    message.variables()), true);
            mailSender.send(mime);
        } catch (Exception e) {
            throw new MailDeliveryFailed(message, e);
        }
    }

    static class MailDeliveryFailed extends RuntimeException {
        MailDeliveryFailed(EmailMessage message, Throwable cause) {
            super("Falha ao enviar o template %s para %s".formatted(message.template(), message.to()), cause);
        }
    }
}
