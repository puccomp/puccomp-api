package br.com.puccomp.api.organization.members.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

/**
 * A partir de quando o histórico desta EJ é confiável.
 *
 * <p>Existe para que o relatório saiba dizer "não sei" em vez de calcular uma janela pela metade e
 * apresentá-la como completa. A qualidade da série daqui pra frente e a qualidade das datas
 * originais são informações distintas, e só esta é conhecida.
 */
@Entity
@Table(name = "organization_tracking")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class OrganizationTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tracked_since", nullable = false, updatable = false)
    private Instant trackedSince;
}
