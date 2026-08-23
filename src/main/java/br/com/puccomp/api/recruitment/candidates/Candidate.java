package br.com.puccomp.api.recruitment.candidates;

import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "candidates")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Candidate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column
    private String linkedinUrl;

    @Column
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
