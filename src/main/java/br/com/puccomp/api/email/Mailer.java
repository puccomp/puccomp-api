package br.com.puccomp.api.email;

/**
 * Vitrine do módulo: o único jeito de outro módulo mandar email.
 *
 * <p>Quem chama descreve a mensagem, não a entrega. Não há sobrecarga que aceite corpo pronto de
 * propósito — foi assim que a redação do convite acabou morando dentro de {@code identity}.
 */
public interface Mailer {

    void send(EmailMessage message);
}
