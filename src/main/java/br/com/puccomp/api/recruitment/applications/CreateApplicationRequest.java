package br.com.puccomp.api.recruitment.applications;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApplicationRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank String university,
        @NotBlank String course,
        @NotBlank String currentTerm,
        String linkedinUrl,
        String portfolioUrl,
        @NotNull @AssertTrue Boolean privacyConsent
) {
}
