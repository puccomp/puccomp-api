package br.com.puccomp.api.email;

import java.util.Map;

public record EmailMessage(
        String to,
        String subject,
        String template,
        Map<String, String> variables
) {
    public EmailMessage {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
