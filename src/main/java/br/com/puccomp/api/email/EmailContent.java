package br.com.puccomp.api.email;

import java.util.Map;

/** Casa cada {@link EmailMessage} com o assunto e o template que a representam. */
record EmailContent(String subject, String html) {

    static EmailContent of(EmailMessage message) {
        return switch (message) {
            case EmailMessage.Invitation invitation -> new EmailContent(
                    "Convite para " + invitation.organizationName(),
                    EmailTemplate.render("convite", Map.of(
                            "organizationName", invitation.organizationName(),
                            "acceptUrl", invitation.acceptUrl(),
                            "validFor", horas(invitation.validFor().toHours()))));

            case EmailMessage.CandidacyReceived candidacy -> new EmailContent(
                    "Inscrição confirmada — " + candidacy.processTitle(),
                    EmailTemplate.render("candidatura-confirmada", Map.of(
                            "candidateName", primeiroNome(candidacy.candidateName()),
                            "processTitle", candidacy.processTitle())));
        };
    }

    private static String horas(long total) {
        return total == 1 ? "1 hora" : total + " horas";
    }

    /** O email trata a pessoa pelo primeiro nome; o cadastro guarda o nome completo. */
    private static String primeiroNome(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
