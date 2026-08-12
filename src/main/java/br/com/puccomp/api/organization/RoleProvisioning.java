package br.com.puccomp.api.organization;

import java.util.UUID;

public interface RoleProvisioning {

    UUID createRole(String name, String description);
}
