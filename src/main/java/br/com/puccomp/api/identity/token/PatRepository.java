package br.com.puccomp.api.identity.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PatRepository extends JpaRepository<PersonalAccessToken, UUID> {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findByAccountIdAndRevokedAtIsNull(UUID accountId);

    Optional<PersonalAccessToken> findByIdAndAccountId(UUID id, UUID accountId);
}
