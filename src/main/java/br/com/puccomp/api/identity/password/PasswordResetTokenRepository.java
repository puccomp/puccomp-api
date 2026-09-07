package br.com.puccomp.api.identity.password;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByAccountIdAndUsedAtIsNull(UUID accountId);
}
