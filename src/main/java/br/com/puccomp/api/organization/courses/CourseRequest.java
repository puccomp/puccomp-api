package br.com.puccomp.api.organization.courses;

import jakarta.validation.constraints.NotBlank;

public record CourseRequest(
        @NotBlank(message = "O nome é obrigatório") String name) {
}