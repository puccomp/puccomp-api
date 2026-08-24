package br.com.puccomp.api.recruitment.candidates;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record CandidateRequest(
        @NotBlank(message = "O nome completo é obrigatório") String fullName,
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "O telefone é obrigatório") @Size(max = 50) String phone,
        @Size(max = 5, message = "São aceitos no máximo 5 links")
        List<@NotBlank(message = "O link não pode ficar em branco")
             @URL(message = "Link inválido")
             @Size(max = 500, message = "O link deve ter no máximo 500 caracteres") String> links
) {
}
