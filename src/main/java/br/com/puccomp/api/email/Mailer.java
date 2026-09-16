package br.com.puccomp.api.email;

public interface Mailer {

    /**
     * Enfileira a entrega e volta na hora; o envio só acontece se a transação em curso confirmar.
     * Falha fica no log e nada a reprocessa.
     */
    void send(EmailMessage message);

    /**
     * Entrega na thread do chamador e deixa a falha subir. Dentro de um listener, isso mantém a
     * publicação incompleta no outbox e o aviso volta a ser tentado.
     */
    void deliver(EmailMessage message);
}
