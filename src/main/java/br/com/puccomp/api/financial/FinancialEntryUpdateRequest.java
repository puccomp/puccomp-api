package br.com.puccomp.api.financial;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialEntryUpdateRequest(
        LocalDate date,
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos e 2 casas decimais")
        BigDecimal value,
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String description,
        FinancialEntryType type,
        @Size(max = 120, message = "A categoria deve ter no máximo 120 caracteres")
        String category,
        @Size(max = 500, message = "A URL do comprovante deve ter no máximo 500 caracteres")
        String receiptUrl) {
}
