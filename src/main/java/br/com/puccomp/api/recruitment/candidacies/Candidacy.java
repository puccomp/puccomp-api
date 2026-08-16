package br.com.puccomp.api.recruitment.candidacies;

import br.com.puccomp.api.recruitment.candidates.Candidate;
import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidacies")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Candidacy extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private SelectionProcess process;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "current_term", nullable = false)
    private int currentTerm;

    @Column(nullable = false)
    private String course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CandidacyStatus status;

    // Instante do aceite, não um booleano: é ele que comprova o consentimento LGPD.
    @Column(name = "privacy_consent_at", nullable = false, updatable = false)
    private Instant privacyConsentAt;
}
