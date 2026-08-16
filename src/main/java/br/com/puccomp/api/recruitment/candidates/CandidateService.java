package br.com.puccomp.api.recruitment.candidates;

import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;

    @Transactional(readOnly = true)
    public CandidateResponse getById(UUID id) {
        var candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidato não encontrado"));

        return CandidateResponse.from(candidate);
    }

    @Transactional
    public CandidateResponse create(CandidateRequest request) {
        if (candidateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email já cadastrado");
        }

        var candidate = Candidate.builder()
                .fullName(request.fullName().trim())
                .email(request.email().trim())
                .phone(request.phone().trim())
                .linkedinUrl(trimmed(request.linkedinUrl()))
                .portfolioUrl(trimmed(request.portfolioUrl()))
                .build();

        var saved = candidateRepository.save(candidate);
        return CandidateResponse.from(saved);
    }

    @Transactional
    public CandidateResponse update(UUID id, CandidateRequest request) {
        var existing = candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidato não encontrado"));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            existing.changeFullName(request.fullName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim();
            candidateRepository.findByEmailIgnoreCase(email).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ConflictException("Email já cadastrado");
                }
            });
            existing.changeEmail(email);
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            existing.changePhone(request.phone().trim());
        }
        if (request.linkedinUrl() != null) {
            existing.changeLinkedinUrl(trimmed(request.linkedinUrl()));
        }
        if (request.portfolioUrl() != null) {
            existing.changePortfolioUrl(trimmed(request.portfolioUrl()));
        }

        var updated = candidateRepository.save(existing);
        return CandidateResponse.from(updated);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
