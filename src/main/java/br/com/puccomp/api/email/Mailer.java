package br.com.puccomp.api.email;

public interface Mailer {

    void send(EmailMessage message);
}
