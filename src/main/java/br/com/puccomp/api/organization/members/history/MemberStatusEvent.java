package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.organization.members.MemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Um evento do ciclo de vida de um membro. Append-only nos fluxos desta entrega: aposentar e
 * reativar acrescentam, nunca apagam nem sobrescrevem — uma saída já contabilizada não pode
 * desaparecer do turnover porque a pessoa voltou depois.
 *
 * <p>{@code Member.status} continua sendo o estado atual; este histórico é a origem de tudo que
 * for temporal, porque o estado atual não sabe reconstruir mês nenhum do passado.
 */
@Entity
@Table(name = "member_status_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class MemberStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    /** Ordem do evento dentro do vínculo. A unicidade em (tenant, membro, sequência) é o que
     *  impede duas transições concorrentes de produzirem uma história incoerente. */
    @Column(nullable = false, updatable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MemberStatusEventKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private MemberStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private MemberStatus toStatus;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
