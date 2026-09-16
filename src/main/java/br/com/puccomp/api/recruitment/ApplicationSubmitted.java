package br.com.puccomp.api.recruitment;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma inscrição entrou no funil, com o que um aviso precisa dizer já materializado: quem escuta
 * roda depois do commit, e voltar ao banco por nome de curso viraria mais consultas.
 */
public record ApplicationSubmitted(
        UUID tenantId,
        UUID applicationId,
        UUID processId,
        String processTitle,
        String candidateName,
        String candidateEmail,
        String courseName,
        boolean hasCv,
        Instant submittedAt
) { }
