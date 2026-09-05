package br.com.puccomp.api.recruitment.applications;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record SubmitCandidateApplicationRequest(
        @Schema(name = "full_name") @NotBlank(message = "O nome completo é obrigatório") @Size(max = 255) String fullName,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") @Size(max = 255) String email,
        @NotBlank(message = "O telefone é obrigatório") @Size(max = 50) String phone,
        @Schema(name = "course_id") @NotNull(message = "O curso é obrigatório") UUID courseId,
        @Schema(name = "current_term") @Min(value = 1, message = "O período mínimo é 1")
        @Max(value = 12, message = "O período máximo é 12") Short currentTerm,
        @Size(max = 5, message = "São aceitos no máximo 5 links")
        List<@NotBlank(message = "O link não pode ficar em branco")
             @URL(message = "Link inválido")
             @Size(max = 500, message = "O link deve ter no máximo 500 caracteres") String> links,
        @Schema(name = "privacy_consent") @NotNull @AssertTrue(message = "É necessário aceitar o tratamento dos dados") Boolean privacyConsent
) { }
