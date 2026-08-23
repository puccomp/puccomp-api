package br.com.puccomp.api.recruitment.candidates;

/**
 * O que {@code candidacies} pode fazer com um candidato: achar pelo e-mail dentro da EJ ou
 * cadastrar na hora da primeira inscrição. Nada de listar, nada de apagar.
 */
public interface CandidateRegistry {

    Candidate findOrRegister(NewCandidate candidate);

    record NewCandidate(String fullName, String email, String phone, String linkedinUrl, String portfolioUrl) {
    }
}
