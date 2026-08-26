package br.com.puccomp.api.recruitment.candidates;

import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidateService implements CandidateRegistry {

    private final CandidateRepository repository;

    @Transactional
    CandidateResponse create(CandidateRequest request) {
        String email = request.email().trim();
        if (repository.existsByEmailIgnoreCase(email))
            throw new ConflictException("Já existe um candidato com esse e-mail");

        
        return CandidateResponse.from(repository.save(Candidate.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .phone(request.phone().trim())
                .links(sanitized(request.links()))
                .build()));
    }

    @Transactional
    CandidateResponse update(UUID id, CandidateRequest request) {
        Candidate candidate = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidato não encontrado"));

        String email = request.email().trim();
        repository.findByEmailIgnoreCase(email).ifPresent(other -> {
            if (!other.getId().equals(id))
                throw new ConflictException("Já existe um candidato com esse e-mail");
        });

        candidate.changeFullName(request.fullName().trim());
        candidate.changeEmail(email);
        candidate.changePhone(request.phone().trim());
        candidate.changeLinks(sanitized(request.links()));

        return CandidateResponse.from(candidate);
    }

    @Override
    @Transactional
    public Candidate findOrRegister(NewCandidate data) {
        String email = data.email().trim();
        return repository.findByEmailIgnoreCase(email)
                .map(existing -> refreshContact(existing, data))
                .orElseGet(() -> repository.save(Candidate.builder()
                        .fullName(data.fullName().trim())
                        .email(email)
                        .phone(data.phone().trim())
                        .links(sanitized(data.links()))
                        .build()));
    }

    /**
     * Quem se inscreve de novo costuma trazer o dado mais recente — telefone novo, sobrenome de
     * casada. Campo ausente não apaga o que já está lá: a ficha antiga continua valendo.
     */
    private Candidate refreshContact(Candidate candidate, NewCandidate data) {
        candidate.changeFullName(data.fullName().trim());
        candidate.changePhone(data.phone().trim());
        List<String> links = sanitized(data.links());
        if (!links.isEmpty()) candidate.changeLinks(links);
        return candidate;
    }

    private static List<String> sanitized(List<String> links) {
        if (links == null) return List.of();
        return links.stream()
                .filter(link -> link != null && !link.isBlank())
                .map(String::trim)
                .toList();
    }
}
