package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberService {

    private final MemberRepository repository;

    Page<MemberResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(MemberResponse::from);
    }

    MemberResponse findById(UUID id) {
        return repository.findById(id)
                .map(MemberResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado"));
    }

    @Transactional
    MemberResponse retire(UUID id) {
        return transition(id, MemberStatus.ALUMNUS);
    }

    @Transactional
    MemberResponse reactivate(UUID id) {
        return transition(id, MemberStatus.ACTIVE);
    }

    private MemberResponse transition(UUID id, MemberStatus status) {
        Member member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado"));
        member.changeStatus(status);
        return MemberResponse.from(member);
    }
}
