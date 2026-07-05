package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "roles", uniqueConstraints =
        @UniqueConstraint(name = "uk_roles_tenant_name", columnNames = {"tenant_id", "name"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Role extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int hierarchyLevel;

    @Column
    private Integer maxSeats;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
