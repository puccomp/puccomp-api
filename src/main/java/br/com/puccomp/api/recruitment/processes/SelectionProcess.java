package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.audit.Auditable;
import br.com.puccomp.api.shared.exception.ConflictException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

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

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void changeStatusTo(SelectionProcessStatus target) {
        if (status == target) return;
        if (!status.canTransitionTo(target))
            throw new ConflictException("Não é possível mudar o processo de %s para %s".formatted(status, target));
        this.status = target;
    }

}
