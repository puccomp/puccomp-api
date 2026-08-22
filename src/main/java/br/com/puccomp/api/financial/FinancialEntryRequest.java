package br.com.puccomp.api.financial;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialEntryRequest(
        @NotNull(message = "A data é obrigatória") LocalDate occurredOn,
        @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos e 2 casas decimais")
        BigDecimal amount,
        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String description,
        @NotNull(message = "O tipo é obrigatório") FinancialEntryType type,
        @NotBlank(message = "A categoria é obrigatória")
        @Size(max = 120, message = "A categoria deve ter no máximo 120 caracteres")
        String category,
        @URL(message = "A URL do comprovante deve ser válida")
        @Size(max = 500, message = "A URL do comprovante deve ter no máximo 500 caracteres")
        String receiptUrl) {
}
