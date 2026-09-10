package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.organization.MembershipHistoryProvisioning;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class MembershipHistoryProvisioningService implements MembershipHistoryProvisioning {

    private final MemberLifecycle lifecycle;
    private final Clock clock;

    @Override
    @Transactional
    public void startTracking() {
        lifecycle.startTracking(clock.instant());
    }
}
