package br.com.puccomp.api.organization.courses;

import java.util.UUID;

public record CourseResponse(UUID id, String name, boolean active) {

    static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getName(), course.isActive());
    }
}
