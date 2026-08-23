package br.com.puccomp.api.recruitment.candidacies;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record SubmitCandidacyRequest(
        @NotBlank(message = "O nome completo é obrigatório") String fullName,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "O telefone é obrigatório") @Size(max = 50) String phone,
        @NotBlank(message = "O curso é obrigatório") String course,
        @Size(max = 50, message = "O período deve ter no máximo 50 caracteres") String currentTerm,
        @Size(max = 5, message = "São aceitos no máximo 5 links")
        List<@NotBlank(message = "O link não pode ficar em branco")
             @URL(message = "Link inválido")
             @Size(max = 500, message = "O link deve ter no máximo 500 caracteres") String> links,
        @NotNull @AssertTrue(message = "É necessário aceitar o tratamento dos dados") Boolean privacyConsent
) { }
