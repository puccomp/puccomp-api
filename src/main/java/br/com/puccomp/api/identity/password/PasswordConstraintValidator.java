package br.com.puccomp.api.identity.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Nulo e vazio são problema do @NotBlank; acumular as duas mensagens só confunde.
        if (password == null || password.isBlank())
            return true;

        List<String> missing = PasswordPolicy.violations(password);
        if (missing.isEmpty())
            return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(PasswordPolicy.describe(missing))
                .addConstraintViolation();
        return false;
    }
}
