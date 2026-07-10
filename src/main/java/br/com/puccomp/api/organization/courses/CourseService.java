package br.com.puccomp.api.organization.courses;

import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.organization.CourseProvisioning;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CourseService implements CourseCatalog, CourseProvisioning {

    private final CourseRepository repository;

    @Transactional(readOnly = true)
    List<CourseResponse> findAll() {
        return repository.findAll(Sort.by("name")).stream().map(CourseResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseOption> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .map(course -> new CourseOption(course.getId(), course.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssignable(UUID courseId) {
        return courseId != null && repository.existsByIdAndActiveTrue(courseId);
    }

    @Override
    @Transactional
    public UUID createCourse(String name) {
        return repository.save(Course.builder().name(name.trim()).build()).getId();
    }
}
