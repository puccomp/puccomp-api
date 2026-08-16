package br.com.puccomp.api.recruitment.candidates;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.util.UUID;

import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "candidates")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "linkedin_url", nullable = true)
    private String linkedinUrl;

    @Column(name = "portfolio_url", nullable = true)
    private String portfolioUrl;

    void changeFullName(String fullName) {
        this.fullName = fullName;
    }

    void changeEmail(String email) {
        this.email = email;
    }

    void changePhone(String phone) {
        this.phone = phone;
    }

    void changeLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    void changePortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }
}
