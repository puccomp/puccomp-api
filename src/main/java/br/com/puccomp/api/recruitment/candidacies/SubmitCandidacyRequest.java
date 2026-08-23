package br.com.puccomp.api.recruitment.candidacies;

import jakarta.validation.constraints.*;

public record SubmitCandidacyRequest(
        @NotBlank(message = "O nome completo é obrigatório") String fullName,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "O telefone é obrigatório") @Size(max = 50) String phone,
        @NotBlank(message = "O curso é obrigatório") String course,
        @NotNull(message = "O período é obrigatório")
        @Min(value = 1, message = "O período deve ser maior que zero") Integer currentTerm,
        String linkedinUrl,
        String portfolioUrl,
        @NotNull @AssertTrue(message = "É necessário aceitar o tratamento dos dados") Boolean privacyConsent
) { }
