package br.com.puccomp.api.financial;

import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_entries")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FinancialEntry extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Data em que o dinheiro entrou ou saiu — não se confunde com a data de registro ({@code createdAt}). */
    @Column(nullable = false)
    private LocalDate occurredOn;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinancialEntryType type;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    void changeOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    void changeDescription(String description) {
        this.description = description;
    }

    void changeType(FinancialEntryType type) {
        this.type = type;
    }

    void changeCategory(String category) {
        this.category = category;
    }

    void changeReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }
}
