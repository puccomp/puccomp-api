package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.courses.Course;
import br.com.puccomp.api.organization.departments.Department;
import br.com.puccomp.api.organization.roles.Role;
import br.com.puccomp.api.shared.reference.Standing;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    /** Cópia do e-mail da conta, para o contrato público e para a busca alcançarem os dois no
     *  mesmo predicado. Nulo em membro sem conta associada. */
    private String email;

    /** Gerada pelo banco a partir de {@code name}; existe só para o JPQL da busca alcançá-la. */
    @Column(name = "search_name", insertable = false, updatable = false)
    private String searchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Standing standing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Preenchido, o membro deixou de existir para quem consome a API. Nulo é o vínculo vivo. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Package-private de propósito: mudar o estado sem registrar o evento correspondente é
    // exatamente o que o histórico de vínculos existe para impedir. Passe pelo MemberLifecycle.
    public void changeStatus(MemberStatus status) {
        this.status = status;
    }

    void assign(Role role, Department department) {
        this.role = role;
        this.department = department;
    }

    // Package-private pelo mesmo motivo do changeStatus: deletar sem fechar o intervalo ativo
    // deixaria o removido contando no turnover para sempre. Passe pelo MemberLifecycle.
    public void delete(Instant at) {
        this.deletedAt = at;
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
