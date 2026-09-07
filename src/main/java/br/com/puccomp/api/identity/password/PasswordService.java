package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.identity.account.Account;
import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.shared.exception.UnauthorizedException;
import br.com.puccomp.api.shared.exception.ValidationException;
import br.com.puccomp.api.shared.token.TokenSecrets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PasswordService {

    private static final String PREFIX = "pwd_";
    private static final String INVALID_TOKEN = "Link de redefinição inválido ou expirado";

    private final AccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Mailer mailer;
    private final PasswordResetProperties properties;

    /**
     * Não distingue e-mail cadastrado de e-mail desconhecido: quem chama aqui não está autenticado,
     * e responder diferente transformaria o endpoint num verificador de quem tem conta. Por isso a
     * conta inexistente ou desativada sai pelo mesmo 202 de sucesso, só sem email.
     */
    @Transactional
    void forgot(ForgotPasswordRequest request) {
        accounts.findByEmailIgnoreCase(request.email().trim())
                .filter(Account::isActive)
                .ifPresent(this::issueResetToken);
    }

    @Transactional
    void reset(ResetPasswordRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = tokens.findByTokenHash(TokenSecrets.sha256Hex(request.token().trim()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ValidationException(INVALID_TOKEN));

        Account account = accounts.findById(token.getAccountId())
                .filter(Account::isActive)
                .orElseThrow(() -> new ValidationException(INVALID_TOKEN));

        account.changePassword(passwordEncoder.encode(request.password()));
        token.markUsed(now);
        invalidateOutstanding(account.getId(), now);
    }

    /**
     * Exige a senha atual mesmo com sessão válida: um token roubado não pode virar troca de senha.
     * Senha atual errada sai como 400 e não 401 de propósito — a sessão continua válida, e o 401
     * faria o front tratar como token expirado e deslogar quem só errou a digitação.
     */
    @Transactional
    void change(AuthPrincipal principal, ChangePasswordRequest request) {
        Account account = accounts.findById(principal.accountId())
                .filter(Account::isActive)
                .orElseThrow(() -> new UnauthorizedException("Conta indisponível"));

        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash()))
            throw new ValidationException("current_password: senha atual incorreta");
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash()))
            throw new ValidationException("new_password: a nova senha deve ser diferente da atual");

        Instant now = Instant.now();
        account.changePassword(passwordEncoder.encode(request.newPassword()));
        invalidateOutstanding(account.getId(), now);
    }

    private void issueResetToken(Account account) {
        Instant now = Instant.now();
        invalidateOutstanding(account.getId(), now);

        String raw = PREFIX + TokenSecrets.randomSecret();
        tokens.save(PasswordResetToken.builder()
                .accountId(account.getId())
                .tokenHash(TokenSecrets.sha256Hex(raw))
                .expiresAt(now.plus(properties.ttl()))
                .build());

        mailer.send(new EmailMessage(
                account.getEmail(),
                "Redefinição de senha",
                "redefinir-senha",
                Map.of(
                        "resetUrl", properties.urlBase() + "?token=" + raw,
                        "validFor", humanizedTtl())));
    }

    /** Só um link vale por vez: pedir de novo, redefinir ou trocar a senha derruba os anteriores. */
    private void invalidateOutstanding(UUID accountId, Instant now) {
        tokens.findByAccountIdAndUsedAtIsNull(accountId).forEach(token -> token.markUsed(now));
    }

    private String humanizedTtl() {
        Duration ttl = properties.ttl();
        if (ttl.toHours() >= 1)
            return ttl.toHours() == 1 ? "1 hora" : ttl.toHours() + " horas";
        return ttl.toMinutes() == 1 ? "1 minuto" : ttl.toMinutes() + " minutos";
    }
}
