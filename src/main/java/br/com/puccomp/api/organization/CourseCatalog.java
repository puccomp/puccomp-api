package br.com.puccomp.api.organization;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CourseCatalog {

    List<CourseOption> listActive();

    /** Só cursos ativos são atribuíveis: desativar um curso o retira do formulário sem apagar histórico. */
    boolean isAssignable(UUID courseId);

    /** Resolve nomes inclusive de curso já desativado — o histórico não pode ficar sem rótulo. */
    Map<UUID, String> namesOf(Collection<UUID> courseIds);

    record CourseOption(UUID id, String name) { }
}
