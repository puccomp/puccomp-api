package br.com.puccomp.api.organization.courses;

import jakarta.validation.constraints.NotBlank;

public record CoursesRequest (@NotBlank(message = "O nome é obrigatório") String name){

}
