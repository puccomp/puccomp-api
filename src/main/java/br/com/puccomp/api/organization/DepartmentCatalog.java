package br.com.puccomp.api.organization;

import java.util.UUID;

public interface DepartmentCatalog {

    boolean isAssignable(UUID departmentId);
}
