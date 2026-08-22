package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.audit.Auditable;
import br.com.puccomp.api.shared.exception.ConflictException;
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

    public void update(String title, String description, Instant opensAt, Instant closesAt) {
        this.title = title;
        this.description = description;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public void changeStatusTo(SelectionProcessStatus target) {
        if (status == target)
            return;
        if (!status.canTransitionTo(target))
            throw new ConflictException("Não é possível mudar o processo de %s para %s".formatted(status, target));
        this.status = target;
    }

    /** Data nula significa "sem limite": aí só o status decide. */
    public boolean acceptsCandidaciesAt(Instant now) {
        return (opensAt == null || !now.isBefore(opensAt))
                && (closesAt == null || now.isBefore(closesAt));
    }
}
