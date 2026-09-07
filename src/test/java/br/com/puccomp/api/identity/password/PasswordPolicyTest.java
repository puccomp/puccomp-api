package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Senha@123", "Ótima#senha", "AAAAAAA!", "Muito longa mas ainda ok!"})
    @DisplayName("senhas que atendem comprimento, maiúscula e caractere especial passam")
    void shouldAcceptCompliantPasswords(String password) {
        assertThat(PasswordPolicy.violations(password)).isEmpty();
    }

    @Test
    @DisplayName("acentuada conta como maiúscula: o público é brasileiro, não ASCII")
    void shouldTreatAccentedLetterAsUppercase() {
        assertThat(PasswordPolicy.violations("Ática@2026")).isEmpty();
    }

    @Test
    @DisplayName("letra acentuada NÃO conta como caractere especial")
    void shouldNotTreatAccentAsSpecialCharacter() {
        assertThat(PasswordPolicy.violations("Senhaça123")).containsExactly("um caractere especial");
    }

    @Test
    @DisplayName("a mensagem diz só o que falta, não recita a política inteira")
    void shouldDescribeOnlyWhatIsMissing() {
        assertThat(PasswordPolicy.describe(PasswordPolicy.violations("senha@123")))
                .isEqualTo("precisa de uma letra maiúscula");
        assertThat(PasswordPolicy.describe(PasswordPolicy.violations("abc")))
                .isEqualTo("precisa de ao menos 8 caracteres, uma letra maiúscula e um caractere especial");
    }

    @Test
    @DisplayName("exatamente 72 caracteres ainda passa: o limite é inclusivo")
    void shouldAcceptExactlyMaxLength() {
        assertThat(PasswordPolicy.violations("A!" + "x".repeat(70))).isEmpty();
    }

    @Test
    @DisplayName("acima de 72 caracteres é recusado: BCrypt truncaria o resto em silêncio")
    void shouldRejectBeyondBcryptLimit() {
        assertThat(PasswordPolicy.violations("A!" + "x".repeat(71)))
                .containsExactly("no máximo 72 caracteres");
    }

    @Test
    @DisplayName("validateNewPassword vira 400 com o campo no prefixo, como bean validation")
    void shouldThrowValidationExceptionWithFieldPrefix() {
        assertThatThrownBy(() -> PasswordPolicy.validateNewPassword("curta"))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("password: precisa de");
        assertThatCode(() -> PasswordPolicy.validateNewPassword("Senha@123")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nulo é recusado sem NullPointerException")
    void shouldRejectNull() {
        assertThat(PasswordPolicy.violations(null)).isNotEmpty();
    }
}
