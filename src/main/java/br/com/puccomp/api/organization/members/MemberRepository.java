package br.com.puccomp.api.organization.members;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MemberRepository extends JpaRepository<Member, UUID>, JpaSpecificationExecutor<Member> {

    /** O fetch descreve a página, não o conjunto: as agregações do resumo não passam por aqui. */
    @Override
    @EntityGraph(attributePaths = {"role", "department", "course"})
    Page<Member> findAll(Specification<Member> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"role", "department", "course"})
    Optional<Member> findById(UUID id);

    /**
     * Trava a linha para mudar o estado. Duas transições concorrentes do mesmo membro produziriam
     * eventos com a mesma sequência — a unicidade os recusaria, mas tarde, já com a projeção
     * atualizada por uma delas. Serializar aqui é mais barato do que reconciliar depois.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findForStatusChange(@Param("id") UUID id);

    @Query("select m.role.id as roleId, m.status as status from Member m where m.id = :id")
    Optional<AccessRow> findAccessById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "role")
    List<Member> findByStatus(MemberStatus status);

    @Query(value = "select id as member_id, tenant_id as tenant_id, standing as standing "
            + "from members where account_id = :accountId and status in ('ACTIVE', 'ALUMNUS')",
            nativeQuery = true)
    List<MembershipRow> findMembershipsByAccountId(@Param("accountId") UUID accountId);

    @Query(value = "select id as member_id, tenant_id as tenant_id, standing as standing "
            + "from members where account_id = :accountId and tenant_id = :tenantId "
            + "and status in ('ACTIVE', 'ALUMNUS')", nativeQuery = true)
    Optional<MembershipRow> findMembership(@Param("accountId") UUID accountId, @Param("tenantId") UUID tenantId);

    interface AccessRow {
        UUID getRoleId();
        MemberStatus getStatus();
    }

    interface MembershipRow {
        UUID getMemberId();
        UUID getTenantId();
        String getStanding();
    }
}
