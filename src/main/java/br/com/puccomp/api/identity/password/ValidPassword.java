package br.com.puccomp.api.identity.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marca um campo que recebe uma senha NOVA, aplicando a {@link PasswordPolicy}.
 *
 * <p>Substitui o par {@code @Size} + regex solto que teria de ser repetido em cada DTO: a regra
 * vive num lugar só e a mensagem diz o que faltou naquela senha, em vez de recitar a política
 * inteira toda vez.
 *
 * <p>Não marca campo de senha ATUAL — ali a senha não está sendo definida, e sim conferida contra
 * um hash que pode ser mais antigo que a política.
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, CONSTRUCTOR, TYPE_USE})
@Retention(RUNTIME)
public @interface ValidPassword {

    String message() default "não atende à política de senha";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
