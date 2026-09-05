package br.com.puccomp.api.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface AccountDirectory {

    Map<UUID, String> findEmails(Collection<UUID> accountIds);
}
