package br.com.puccomp.api.recruitment.candidacies;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitCandidacyRequest(
        @NotBlank(message = "O nome completo é obrigatório") String fullName,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "O telefone é obrigatório") @Size(max = 50) String phone,
        @NotBlank(message = "O curso é obrigatório") String course,
        @NotBlank(message = "O período é obrigatório") @Size(max = 50) String currentTerm,
        String linkedinUrl,
        String portfolioUrl,
        @NotNull @AssertTrue(message = "É necessário aceitar o tratamento dos dados") Boolean privacyConsent
) { }
