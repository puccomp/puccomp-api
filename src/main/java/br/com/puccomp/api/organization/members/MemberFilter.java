package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.exception.ValidationException;
import br.com.puccomp.api.shared.reference.Standing;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.BindParam;

import java.util.UUID;

/**
 * Recortes do quadro atual. Todos opcionais e combinados por AND; nulo significa "não filtra por isto".
 *
 * <p>O {@code @BindParam} é o que liga {@code course_id} da query a {@code courseId} do record: a
 * estratégia snake_case do Jackson vale para corpo de requisição, não para parâmetro de query.
 *
 * <p>{@code departmentId} continua aceito como alias depreciado de {@code department_id} porque já
 * foi publicado. Ele vem em um componente próprio, e não sobrescrevendo o outro, para que enviar os
 * dois com valores diferentes seja um erro explícito em vez de um dos dois vencer em silêncio.
 */
public record MemberFilter(
        @BindParam("department_id")
        @Schema(name = "department_id", description = "Diretoria atual do membro")
        UUID departmentId,

        @BindParam("departmentId")
        @Schema(name = "departmentId", deprecated = true,
                description = "Alias depreciado de department_id. Enviar os dois com valores "
                        + "diferentes é 400; iguais é aceito. A remoção exige mudança de versão")
        UUID legacyDepartmentId,

        @BindParam("role_id")
        @Schema(name = "role_id", description = "Cargo atual do membro")
        UUID roleId,

        @BindParam("course_id")
        @Schema(name = "course_id", description = "Curso atual do membro")
        UUID courseId,

        @Schema(description = "Um valor de MemberStatus; sem ele, todos os estados entram")
        MemberStatus status,

        @Schema(description = "Um valor de Standing")
        Standing standing,

        @BindParam("has_role")
        @Schema(name = "has_role", description = "true traz só quem tem cargo; false só quem não tem")
        Boolean hasRole,

        @BindParam("has_department")
        @Schema(name = "has_department", description = "true traz só quem tem diretoria; false só quem não tem")
        Boolean hasDepartment
) {

    /**
     * Contradição entre presença e identidade é recusada, não resolvida: {@code has_role=false} com
     * {@code role_id} não descreve nenhum conjunto, e devolver zero fingiria que a pergunta fazia
     * sentido. A combinação com {@code has_*=true} é redundante, mas coerente, e passa.
     */
    public MemberFilter {
        if (departmentId != null && legacyDepartmentId != null
                && !departmentId.equals(legacyDepartmentId))
            throw new ValidationException(
                    "department_id e departmentId foram enviados com valores diferentes; "
                            + "departmentId é um alias depreciado do mesmo filtro");

        UUID department = departmentId != null ? departmentId : legacyDepartmentId;
        if (Boolean.FALSE.equals(hasRole) && roleId != null)
            throw new ValidationException("has_role=false não combina com role_id");
        if (Boolean.FALSE.equals(hasDepartment) && department != null)
            throw new ValidationException("has_department=false não combina com department_id");
    }

    /** A diretoria efetivamente filtrada, venha ela do parâmetro atual ou do alias. */
    public UUID department() {
        return departmentId != null ? departmentId : legacyDepartmentId;
    }
}
