package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de uso único para redefinir a senha sem estar autenticado. Só o SHA-256 é persistido: um
 * vazamento do banco não devolve links utilizáveis, mesma escolha do convite.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordResetToken extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Marca tanto o token consumido quanto o que foi substituído por um pedido mais novo. */
    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }
}
