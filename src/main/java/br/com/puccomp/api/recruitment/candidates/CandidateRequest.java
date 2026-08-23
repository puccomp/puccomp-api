package br.com.puccomp.api.recruitment.candidates;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateRequest(
        @NotBlank(message = "O nome completo é obrigatório") String fullName,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "O telefone é obrigatório") @Size(max = 50) String phone,
        String linkedinUrl,
        String portfolioUrl
) {
}
