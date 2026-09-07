package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.identity.account.*;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.shared.exception.ValidationException;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.token.TokenSecrets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock private AccountRepository accounts;
    @Mock private PasswordResetTokenRepository tokens;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Mailer mailer;
    @Mock private PasswordResetProperties properties;
    @InjectMocks private PasswordService service;

    private Account account() {
        return Account.builder()
                .id(UUID.randomUUID())
                .email("membro@ej.dev")
                .passwordHash("hash-atual")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private PasswordResetToken token(UUID accountId, Instant expiresAt) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .tokenHash(TokenSecrets.sha256Hex("pwd_token"))
                .expiresAt(expiresAt)
                .build();
    }

    private AuthPrincipal principal(UUID accountId) {
        return new AuthPrincipal(accountId, "membro@ej.dev", UUID.randomUUID(), UUID.randomUUID(),
                Standing.MEMBER, null);
    }

    @Test
    @DisplayName("forgot gera token pwd_ hasheado e manda o link por email")
    void shouldIssueResetToken() {
        Account account = account();
        when(accounts.findByEmailIgnoreCase("membro@ej.dev")).thenReturn(Optional.of(account));
        when(tokens.findByAccountIdAndUsedAtIsNull(account.getId())).thenReturn(List.of());
        when(properties.ttl()).thenReturn(Duration.ofHours(1));
        when(properties.urlBase()).thenReturn("http://localhost/redefinir-senha");

        service.forgot(new ForgotPasswordRequest("membro@ej.dev"));

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).hasSize(64);
        assertThat(saved.getValue().getAccountId()).isEqualTo(account.getId());

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailer).send(sent.capture());
        assertThat(sent.getValue().template()).isEqualTo("redefinir-senha");
        assertThat(sent.getValue().to()).isEqualTo("membro@ej.dev");
        assertThat(sent.getValue().variables().get("resetUrl"))
                .startsWith("http://localhost/redefinir-senha?token=pwd_");
        assertThat(sent.getValue().variables().get("validFor")).isEqualTo("1 hora");
    }

    @Test
    @DisplayName("forgot para e-mail sem conta não vaza nada: nenhum email, nenhum token, sem erro")
    void shouldStaySilentForUnknownEmail() {
        when(accounts.findByEmailIgnoreCase("ninguem@ej.dev")).thenReturn(Optional.empty());

        service.forgot(new ForgotPasswordRequest("ninguem@ej.dev"));

        verify(tokens, never()).save(any());
        verify(mailer, never()).send(any());
    }

    @Test
    @DisplayName("forgot para conta desativada também é silencioso (mesma resposta do e-mail desconhecido)")
    void shouldStaySilentForDisabledAccount() {
        Account desativada = Account.builder()
                .id(UUID.randomUUID())
                .email("membro@ej.dev")
                .passwordHash("hash-atual")
                .status(AccountStatus.DISABLED)
                .build();
        when(accounts.findByEmailIgnoreCase("membro@ej.dev")).thenReturn(Optional.of(desativada));

        service.forgot(new ForgotPasswordRequest("membro@ej.dev"));

        verify(tokens, never()).save(any());
        verify(mailer, never()).send(any());
    }

    @Test
    @DisplayName("pedir um link novo derruba o anterior: só um vale por vez")
    void shouldInvalidatePreviousTokenWhenReissuing() {
        Account account = account();
        PasswordResetToken anterior = token(account.getId(), Instant.now().plusSeconds(3600));
        when(accounts.findByEmailIgnoreCase("membro@ej.dev")).thenReturn(Optional.of(account));
        when(tokens.findByAccountIdAndUsedAtIsNull(account.getId())).thenReturn(List.of(anterior));
        when(properties.ttl()).thenReturn(Duration.ofHours(1));
        when(properties.urlBase()).thenReturn("http://localhost/redefinir-senha");

        service.forgot(new ForgotPasswordRequest("membro@ej.dev"));

        assertThat(anterior.getUsedAt()).isNotNull();
        assertThat(anterior.isUsable(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("reset troca o hash da senha e queima o token")
    void shouldResetPasswordAndConsumeToken() {
        Account account = account();
        PasswordResetToken token = token(account.getId(), Instant.now().plusSeconds(3600));
        when(tokens.findByTokenHash(TokenSecrets.sha256Hex("pwd_token"))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("senha-nova-123")).thenReturn("hash-novo");
        when(tokens.findByAccountIdAndUsedAtIsNull(account.getId())).thenReturn(List.of());

        service.reset(new ResetPasswordRequest("pwd_token", "senha-nova-123"));

        assertThat(account.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("token já usado é rejeitado (400): o link vale uma vez só")
    void shouldRejectAlreadyUsedToken() {
        PasswordResetToken token = token(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        token.markUsed(Instant.now());
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("pwd_token", "senha-nova-123")))
                .isInstanceOf(ValidationException.class);
        verify(accounts, never()).findById(any());
    }

    @Test
    @DisplayName("token expirado é rejeitado (400), sem tocar na conta")
    void shouldRejectExpiredToken() {
        when(tokens.findByTokenHash(any()))
                .thenReturn(Optional.of(token(UUID.randomUUID(), Instant.now().minusSeconds(1))));

        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("pwd_token", "senha-nova-123")))
                .isInstanceOf(ValidationException.class);
        verify(accounts, never()).findById(any());
    }

    @Test
    @DisplayName("token desconhecido é rejeitado (400)")
    void shouldRejectUnknownToken() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("pwd_token", "senha-nova-123")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("change confirma a senha atual, grava a nova e derruba links de reset pendentes")
    void shouldChangePassword() {
        Account account = account();
        PasswordResetToken pendente = token(account.getId(), Instant.now().plusSeconds(3600));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("senha-atual", "hash-atual")).thenReturn(true);
        when(passwordEncoder.matches("senha-nova-123", "hash-atual")).thenReturn(false);
        when(passwordEncoder.encode("senha-nova-123")).thenReturn("hash-novo");
        when(tokens.findByAccountIdAndUsedAtIsNull(account.getId())).thenReturn(List.of(pendente));

        service.change(principal(account.getId()),
                new ChangePasswordRequest("senha-atual", "senha-nova-123"));

        assertThat(account.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(pendente.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("change com senha atual errada é 400, não 401: a sessão continua válida")
    void shouldRejectWrongCurrentPassword() {
        Account account = account();
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("errada", "hash-atual")).thenReturn(false);

        assertThatThrownBy(() -> service.change(principal(account.getId()),
                new ChangePasswordRequest("errada", "senha-nova-123")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("current_password");
        assertThat(account.getPasswordHash()).isEqualTo("hash-atual");
    }

    @Test
    @DisplayName("change repetindo a senha atual é rejeitado (400)")
    void shouldRejectSamePassword() {
        Account account = account();
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("senha-atual", "hash-atual")).thenReturn(true);

        assertThatThrownBy(() -> service.change(principal(account.getId()),
                new ChangePasswordRequest("senha-atual", "senha-atual")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("new_password");
        verify(passwordEncoder, never()).encode(any());
    }
}
