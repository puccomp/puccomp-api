package br.com.puccomp.api.notification;

import br.com.puccomp.api.identity.AccountDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Resolve o endereço de uma conta. Conta sem e-mail conhecido não é erro: não há a quem avisar. */
@Component
@RequiredArgsConstructor
class Recipients {

    private final AccountDirectory accounts;

    Optional<String> emailOf(UUID accountId) {
        if (accountId == null) return Optional.empty();
        return Optional.ofNullable(accounts.findEmails(List.of(accountId)).get(accountId));
    }

    static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }
}
