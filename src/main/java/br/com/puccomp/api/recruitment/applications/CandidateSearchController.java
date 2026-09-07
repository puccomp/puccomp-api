package br.com.puccomp.api.recruitment.applications;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inscrições")
@RestController
@RequestMapping("/v1/recruitment/applications")
@RequiredArgsConstructor
public class CandidateSearchController {

    private final CandidateApplicationService service;

    @Operation(summary = "Busca inscrições em todos os processos seletivos da EJ",
            description = "Responde \"essa pessoa já se inscreveu antes?\": diferente da listagem por "
                    + "processo, varre o histórico inteiro da EJ. q casa nome ou e-mail ignorando acento "
                    + "e caixa; sem q, devolve o histórico completo paginado. Aceita os mesmos filtros da "
                    + "listagem por processo: course_id, min_term, max_term, has_cv, from e to.")
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public Page<CandidateApplicationResponse> search(
            @ParameterObject CandidateApplicationFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.searchAcrossProcesses(filter, pageable);
    }
}
