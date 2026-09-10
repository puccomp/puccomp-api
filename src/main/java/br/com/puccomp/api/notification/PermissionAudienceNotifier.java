package br.com.puccomp.api.notification;

import br.com.puccomp.api.authorization.PermissionResolver;
import br.com.puccomp.api.email.EmailMessage;
import br.com.puccomp.api.email.Mailer;
import br.com.puccomp.api.identity.AccountDirectory;
import br.com.puccomp.api.organization.MemberDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PermissionAudienceNotifier implements AudienceNotifier {

    private final MemberDirectory members;
    private final PermissionResolver permissions;
    private final AccountDirectory accounts;
    private final Mailer mailer;

    @Override
    @Transactional(readOnly = true)
    public void notifyPermissionHolders(String permission, String subject, String template,
                                        Map<String, String> variables) {
        List<MemberDirectory.ActiveMember> audience = members.listActiveMembers();
        if (audience.isEmpty()) return;

        Set<UUID> allowed = permissions.filterWithPermission(audience.stream()
                .map(PermissionAudienceNotifier::asSubject).toList(), permission);
        if (allowed.isEmpty()) return;

        List<UUID> accountIds = audience.stream()
                .filter(member -> allowed.contains(member.id()))
                .map(member -> member.accountId())
                .toList();

        accounts.findEmails(accountIds).values().stream().distinct()
                .forEach(email -> mailer.send(new EmailMessage(email, subject, template, variables)));
    }

    private static PermissionResolver.Subject asSubject(MemberDirectory.ActiveMember member) {
        return new PermissionResolver.Subject(member.id(), member.roleId(), member.standing(), false);
    }
}
