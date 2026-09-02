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

/**
 * Entrega por SMTP — mailpit no dev, SES em produção. É o mesmo caminho de código nos dois: trocar
 * de provedor é mexer em {@code spring.mail.*}, não aqui.
 *
 * <p>Duas decisões que valem mais que o código:
 *
 * <p><b>Entrega depois do commit.</b> Havendo transação aberta, o envio espera o commit. Sem isso,
 * uma inscrição duplicada — que só estoura no commit, no índice único — mandava "inscrição
 * confirmada" para uma inscrição que não chegou a existir.
 *
 * <p><b>Falha não propaga.</b> Quando o envio roda, a operação de negócio já foi gravada; derrubar
 * a request depois disso transformaria um cadastro bem-sucedido em 500. Fica em ERROR porque email
 * de convite que não sai é invisível para todo mundo — quem convidou acha que enviou, quem foi
 * convidado nunca soube.
 */
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
        EmailContent content = EmailContent.of(message);
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(message.to());
            helper.setSubject(content.subject());
            helper.setText(content.html(), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Falha ao enviar {} para {}: {}",
                    message.getClass().getSimpleName(), message.to(), e.getMessage(), e);
        }
    }
}
