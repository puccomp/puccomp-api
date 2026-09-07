package br.com.puccomp.api.recruitment.applications;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.BindParam;

import java.time.Instant;
import java.util.UUID;

/**
 * Recortes da triagem. Todos opcionais e combináveis; nulo significa "não filtra por isto".
 *
 * <p>O {@code @BindParam} é o que liga {@code course_id} da query a {@code courseId} do record: a
 * estratégia snake_case do Jackson vale para corpo de requisição, não para parâmetro de query, e o
 * {@code @Schema} só documenta.
 */
public record CandidateApplicationFilter(
        @Schema(description = "Busca por nome ou e-mail, ignorando acento e caixa. "
                + "Termos com menos de 2 caracteres são desconsiderados.")
        String q,

        @BindParam("course_id")
        @Schema(name = "course_id", description = "Curso do catálogo da EJ")
        UUID courseId,

        @BindParam("min_term")
        @Schema(name = "min_term", description = "Período mínimo, inclusive")
        Short minTerm,

        @BindParam("max_term")
        @Schema(name = "max_term", description = "Período máximo, inclusive")
        Short maxTerm,

        @BindParam("has_cv")
        @Schema(name = "has_cv", description = "true traz só quem anexou currículo; false só quem não anexou")
        Boolean hasCv,

        @Schema(description = "Inscrições enviadas a partir deste instante, inclusive")
        Instant from,

        @Schema(description = "Inscrições enviadas até este instante, inclusive")
        Instant to
) { }
