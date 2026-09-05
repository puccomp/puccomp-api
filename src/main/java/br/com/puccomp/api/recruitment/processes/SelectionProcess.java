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

    public void update(String title, String description, Instant opensAt, Instant closesAt, Instant resultAt) {
        validateWindow(opensAt, closesAt, resultAt);
        this.title = title;
        this.description = description;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.resultAt = resultAt;
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

    private static void validateWindow(Instant opensAt, Instant closesAt, Instant resultAt) {
        if (opensAt != null && closesAt != null && !closesAt.isAfter(opensAt))
            throw new ValidationException("O encerramento das inscrições precisa ser depois da abertura");

        Instant lastKnown = closesAt != null ? closesAt : opensAt;
        if (resultAt != null && lastKnown != null && resultAt.isBefore(lastKnown))
            throw new ValidationException("A divulgação do resultado não pode ser antes do fim das inscrições");
    }
}
