package br.com.puccomp.api.financial;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialEntryResponse(
        UUID id,
        LocalDate date,
        BigDecimal value,
        String description,
        FinancialEntryType type,
        String category,
        String receiptUrl,
        Instant createdAt,
        Instant updatedAt) {

    static FinancialEntryResponse from(FinancialEntry entry) {
        return new FinancialEntryResponse(
                entry.getId(),
                entry.getDate(),
                entry.getValue(),
                entry.getDescription(),
                entry.getType(),
                entry.getCategory(),
                entry.getReceiptUrl(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
