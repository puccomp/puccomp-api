package br.com.puccomp.api.financial;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Atualização parcial: campo ausente (null) fica como está; {@code receiptUrl} em branco limpa o comprovante.
 * Os campos de texto usam {@code @Pattern} em vez de {@code @NotBlank} porque aqui null é válido
 * (significa "não mexe") e só o branco explícito deve ser recusado.
 */
public record FinancialEntryUpdateRequest(
        LocalDate occurredOn,
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos e 2 casas decimais")
        BigDecimal amount,
        @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL, message = "A descrição não pode ficar em branco")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String description,
        FinancialEntryType type,
        @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL, message = "A categoria não pode ficar em branco")
        @Size(max = 120, message = "A categoria deve ter no máximo 120 caracteres")
        String category,
        @URL(message = "A URL do comprovante deve ser válida")
        @Size(max = 500, message = "A URL do comprovante deve ter no máximo 500 caracteres")
        String receiptUrl) {
}
