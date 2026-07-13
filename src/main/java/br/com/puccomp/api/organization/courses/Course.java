package br.com.puccomp.api.organization.courses;

import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "courses", uniqueConstraints =
        @UniqueConstraint(name = "uk_courses_tenant_name", columnNames = {"tenant_id", "name"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Course extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
