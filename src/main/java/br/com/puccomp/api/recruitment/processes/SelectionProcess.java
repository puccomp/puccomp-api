package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.audit.Auditable;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ValidationException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "selection_processes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SelectionProcess extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String title;

    @Column(name = "search_title", insertable = false, updatable = false)
    private String searchTitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SelectionProcessStatus status;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "result_at")
    private Instant resultAt;

    @Column(name = "min_term")
    private Short minTerm;

    @Column(name = "max_term")
    private Short maxTerm;

    public void update(String title, String description, Instant opensAt, Instant closesAt, Instant resultAt,
                       Short minTerm, Short maxTerm) {
        validateWindow(opensAt, closesAt, resultAt);
        validateTermRange(minTerm, maxTerm);
        this.title = title;
        this.description = description;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.resultAt = resultAt;
        this.minTerm = minTerm;
        this.maxTerm = maxTerm;
    }

    public boolean restrictsTerm() {
        return minTerm != null || maxTerm != null;
    }

    /**
     * Sem faixa declarada, qualquer período serve — inclusive nenhum. Com faixa, a inscrição sem
     * período é recusada: não dá para conferir a regra, e deixar passar a tornaria decorativa.
     */
    public boolean acceptsTerm(Short term) {
        if (!restrictsTerm()) return true;
        if (term == null) return false;
        if (minTerm != null && term < minTerm) return false;
        return maxTerm == null || term <= maxTerm;
    }

    public void changeStatusTo(SelectionProcessStatus target, Instant at) {
        SelectionProcessStatus current = effectiveStatus(at);
        if (current == target) return;
        if (!current.canTransitionTo(target))
            throw new ConflictException("Não é possível mudar o processo de %s para %s".formatted(current, target));
        this.status = target;
    }

    /**
     * O status que o mundo vê. Difere do gravado num caso só: passado o prazo de inscrição, um
     * processo ainda gravado como OPEN já está de fato em avaliação. Derivar evita depender de um
     * job para virar o estado — e evita a janela em que ele aceitaria inscrição fora do prazo.
     */
    public SelectionProcessStatus effectiveStatus(Instant at) {
        if (status == SelectionProcessStatus.OPEN && closesAt != null && !at.isBefore(closesAt))
            return SelectionProcessStatus.IN_REVIEW;
        return status;
    }

    public boolean isAcceptingApplications(Instant at) {
        if (status != SelectionProcessStatus.OPEN) return false;
        if (opensAt != null && at.isBefore(opensAt)) return false;
        return closesAt == null || at.isBefore(closesAt);
    }

    private static void validateTermRange(Short minTerm, Short maxTerm) {
        if (minTerm != null && maxTerm != null && minTerm > maxTerm)
            throw new ValidationException("O período mínimo não pode ser maior que o máximo");
    }

    private static void validateWindow(Instant opensAt, Instant closesAt, Instant resultAt) {
        if (opensAt != null && closesAt != null && !closesAt.isAfter(opensAt))
            throw new ValidationException("O encerramento das inscrições precisa ser depois da abertura");

        Instant lastKnown = closesAt != null ? closesAt : opensAt;
        if (resultAt != null && lastKnown != null && resultAt.isBefore(lastKnown))
            throw new ValidationException("A divulgação do resultado não pode ser antes do fim das inscrições");
    }
}
