package br.com.puccomp.api.identity.invitation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Page<Invitation> findByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNull(UUID tenantId, Pageable pageable);
}
