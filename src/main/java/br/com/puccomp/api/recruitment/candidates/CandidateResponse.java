package br.com.puccomp.api.recruitment.candidates;

import java.util.List;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        List<String> links) {
    static CandidateResponse from(Candidate candidate) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getLinks());
    }
}
