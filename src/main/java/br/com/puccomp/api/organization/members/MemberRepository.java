package br.com.puccomp.api.organization.members;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MemberRepository extends JpaRepository<Member, UUID> {

    @EntityGraph(attributePaths = {"role", "department"})
    Page<Member> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"role", "department"})
    Page<Member> findAllByDepartmentId(UUID departmentId, Pageable pageable);

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<Member> findById(UUID id);

    @Query("select m.role.id from Member m where m.id = :id")
    Optional<UUID> findRoleIdById(UUID id);

    @Query("select m.status from Member m where m.id = :id")
    Optional<MemberStatus> findStatusById(UUID id);

    @Query(value = "select id as member_id, tenant_id as tenant_id, standing as standing "
            + "from members where account_id = :accountId and status in ('ACTIVE', 'ALUMNUS')",
            nativeQuery = true)
    List<MembershipRow> findMembershipsByAccountId(@Param("accountId") UUID accountId);

    @Query(value = "select id as member_id, tenant_id as tenant_id, standing as standing "
            + "from members where account_id = :accountId and tenant_id = :tenantId "
            + "and status in ('ACTIVE', 'ALUMNUS')", nativeQuery = true)
    Optional<MembershipRow> findMembership(@Param("accountId") UUID accountId, @Param("tenantId") UUID tenantId);

    interface MembershipRow {
        UUID getMemberId();
        UUID getTenantId();
        String getStanding();
    }
}
