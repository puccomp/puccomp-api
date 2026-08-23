package br.com.puccomp.api.recruitment.candidates;

import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .linkedinUrl(trimmed(request.linkedinUrl()))
                .portfolioUrl(trimmed(request.portfolioUrl()))
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
        candidate.changeLinkedinUrl(trimmed(request.linkedinUrl()));
        candidate.changePortfolioUrl(trimmed(request.portfolioUrl()));

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
                        .linkedinUrl(trimmed(data.linkedinUrl()))
                        .portfolioUrl(trimmed(data.portfolioUrl()))
                        .build()));
    }

    /**
     * Quem se inscreve de novo costuma trazer o dado mais recente — telefone novo, sobrenome de
     * casada. Campo ausente não apaga o que já está lá: a ficha antiga continua valendo.
     */
    private Candidate refreshContact(Candidate candidate, NewCandidate data) {
        candidate.changeFullName(data.fullName().trim());
        candidate.changePhone(data.phone().trim());
        if (trimmed(data.linkedinUrl()) != null) candidate.changeLinkedinUrl(trimmed(data.linkedinUrl()));
        if (trimmed(data.portfolioUrl()) != null) candidate.changePortfolioUrl(trimmed(data.portfolioUrl()));
        return candidate;
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
