package br.com.puccomp.api.identity.password;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code urlBase} é a página do front que recebe o {@code ?token=}; a API não serve essa tela.
 * O {@code ttl} é curto de propósito: o link é uma credencial que trafega por email.
 *
 * <p>O {@code @Pattern} existe porque o binder de configuração não reclama de placeholder que não
 * resolve — ele entrega a string {@code "${PASSWORD_RESET_URL}"} crua. Sem a validação, esquecer a
 * variável no ambiente não derrubaria o boot: mandaria email com link quebrado, e o erro só
 * apareceria em quem tentasse recuperar a senha.
 */
@Validated
@ConfigurationProperties("puccomp.security.password-reset")
public record PasswordResetProperties(
        @Pattern(regexp = "^https?://.+", message = "precisa ser uma URL http(s); confira PASSWORD_RESET_URL")
        String urlBase,
        @NotNull Duration ttl
) { }
