package br.com.puccomp.api.organization.courses;

import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * O formulário público de inscrição precisa saber quais cursos a EJ aceita — sem isso o candidato
 * não teria como mandar um course_id válido.
 */
@Tag(name = "Inscrição pública")
@SecurityRequirements
@RestController
@RequestMapping("/v1/public/{orgSlug}/courses")
@RequiredArgsConstructor
public class PublicCourseController {

    private final CourseCatalog catalog;

    @Operation(summary = "Lista os cursos que a EJ aceita hoje")
    @ApiResponse(responseCode = "404", description = "EJ não encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public List<CourseCatalog.CourseOption> listAccepted() {
        return catalog.listActive();
    }
}
