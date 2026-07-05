package br.com.puccomp.api.authorization.grant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MemberPermissionRepository extends JpaRepository<MemberPermission, UUID> {

    List<MemberPermission> findByMemberId(UUID memberId);
}
