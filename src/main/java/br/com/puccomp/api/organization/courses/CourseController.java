package br.com.puccomp.api.organization.courses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Cursos")
@RestController
@RequestMapping("/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService service;

    @Operation(summary = "Lista os cursos que a EJ aceita")
    @GetMapping
    public List<CourseResponse> getAll() {
        return service.findAll();
    }
}
