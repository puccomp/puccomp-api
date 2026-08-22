package br.com.puccomp.api.organization;

import java.util.List;
import java.util.UUID;

public interface CourseCatalog {

    List<CourseOption> listActive();

    boolean isAssignable(UUID courseId);

    record CourseOption(UUID id, String name) { }
}
