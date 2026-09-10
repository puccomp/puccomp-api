package br.com.puccomp.api.organization.members.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface OrganizationTrackingRepository extends JpaRepository<OrganizationTracking, UUID> {

    /** O filtro de tenant do Hibernate já limita à EJ corrente: existe no máximo uma linha. */
    Optional<OrganizationTracking> findFirstBy();
}
