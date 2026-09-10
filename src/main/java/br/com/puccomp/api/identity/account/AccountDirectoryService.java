package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.AccountDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AccountDirectoryService implements AccountDirectory {

    private final AccountRepository accounts;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findEmails(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        return accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(candidate -> candidate.getId(), candidate -> candidate.getEmail()));
    }
}
