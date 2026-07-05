package br.com.puccomp.api.shared.reference;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Referências")
@RestController
@RequestMapping("/v1/courses")
public class CourseController {

    @Operation(summary = "Lista os cursos disponíveis (valor + label)")
    @GetMapping
    public List<ReferenceValue> getAll() {
        return Arrays.stream(Course.values())
                .map(course -> new ReferenceValue(course.name(), course.getLabel()))
                .toList();
    }
}
