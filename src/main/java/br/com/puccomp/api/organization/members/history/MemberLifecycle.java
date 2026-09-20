package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.organization.MemberStatusChanged;
import br.com.puccomp.api.organization.members.Member;
import br.com.puccomp.api.organization.members.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * O único caminho por onde o estado de um membro muda.
 *
 * <p>Centralizado porque um {@code setStatus} solto em qualquer serviço produziria uma mudança sem
 * evento, e o buraco só apareceria meses depois, num turnover que não fecha. A projeção
 * ({@code Member.status}) e o evento são gravados na mesma transação: ou os dois, ou nenhum.
 */
@Service
@RequiredArgsConstructor
public class MemberLifecycle {

    private final MemberStatusEventRepository events;
    private final OrganizationTrackingRepository tracking;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /** Criação de membro — inclusive pelo aceite de convite e pelo seeder. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreation(Member member) {
        append(member.getId(), MemberStatusEventKind.CREATED, null, member.getStatus());
    }

    /**
     * Transição para o mesmo estado é no-op: repetir "aposentar" não pode duplicar a saída nem
     * mover a data dela.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void changeStatus(Member member, MemberStatus target) {
        MemberStatus current = member.getStatus();
        if (current == target) return;
        member.changeStatus(target);
        append(member.getId(), MemberStatusEventKind.STATUS_CHANGED, current, target);
        // Aqui, e não no serviço que chamou: é o funil por onde toda mudança passa.
        transitionOf(target).ifPresent(transition -> publish(member, transition));
    }

    /**
     * Saída da EJ. O evento é gravado mesmo com a projeção {@code status} intacta: quem sai estando
     * ativo precisa ter o intervalo encerrado, senão segue contando no quadro médio do turnover
     * para sempre. Deletar duas vezes não gera duas saídas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Member member, Instant at) {
        if (member.isDeleted()) return;
        member.delete(at);
        append(member.getId(), MemberStatusEventKind.DELETED, member.getStatus(), member.getStatus());
        publish(member, MemberStatusChanged.Transition.REMOVED);
    }

    /** Devolve o vínculo no estado em que ele saiu — reativação, nunca uma segunda admissão. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(Member member) {
        if (!member.isDeleted()) return;
        member.restore();
        append(member.getId(), MemberStatusEventKind.RESTORED, member.getStatus(), member.getStatus());
        publish(member, MemberStatusChanged.Transition.RESTORED);
    }

    private static Optional<MemberStatusChanged.Transition> transitionOf(MemberStatus target) {
        return Optional.of(switch (target) {
            case ALUMNUS -> MemberStatusChanged.Transition.RETIRED;
            case ACTIVE -> MemberStatusChanged.Transition.REACTIVATED;
        });
    }

    private void publish(Member member, MemberStatusChanged.Transition transition) {
        publisher.publishEvent(new MemberStatusChanged(member.getTenantId(), member.getId(),
                member.getAccountId(), member.getName(), transition, clock.instant()));
    }

    /** Marco de cobertura da EJ. Vale inclusive para EJ que ainda não tem membro nenhum. */
    @Transactional(propagation = Propagation.MANDATORY)
    void startTracking(Instant at) {
        if (tracking.findFirstBy().isPresent()) return;
        tracking.save(OrganizationTracking.builder().trackedSince(at).build());
    }

    private void append(UUID memberId, MemberStatusEventKind kind, MemberStatus from, MemberStatus to) {
        events.save(MemberStatusEvent.builder()
                .memberId(memberId)
                .sequence(events.lastSequenceOf(memberId) + 1)
                .kind(kind)
                .fromStatus(from)
                .toStatus(to)
                .occurredAt(clock.instant())
                .build());
    }
}
