package br.com.puccomp.api.recruitment.candidacies;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitCandidacyRequest(
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        String fullName,
        @Size(max = 50) String phone,
        @NotBlank(message = "O curso é obrigatório") String course,
        @NotNull(message = "O período é obrigatório") @Min(value = 1, message = "O período deve ser maior que 0") Integer currentTerm,
        String linkedinUrl,
        String portfolioUrl,
        @NotNull @AssertTrue(message = "É necessário aceitar o tratamento dos dados") Boolean privacyConsent) {

    public SubmitCandidacyRequest(String email, String fullName, String course, Boolean privacyConsent) {
        this(email, fullName, "", course, 1, "", "", privacyConsent);
    }
}
