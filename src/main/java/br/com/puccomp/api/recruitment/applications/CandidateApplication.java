package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.recruitment.processes.SelectionProcess;
import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidate_applications")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CandidateApplication extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false, updatable = false)
    private SelectionProcess process;

    @Column(name = "full_name", nullable = false, updatable = false)
    private String fullName;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false, updatable = false, length = 50)
    private String phone;

    @Column(nullable = false, updatable = false)
    private String course;

    @Column(name = "current_term", updatable = false, length = 50)
    private String currentTerm;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "candidate_application_links",
            joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "link_order")
    @Column(name = "url", nullable = false, length = 500)
    @Builder.Default
    private List<String> links = new ArrayList<>();

    @Column(name = "privacy_consent_at", nullable = false, updatable = false)
    private Instant privacyConsentAt;

    public List<String> getLinks() {
        return links == null ? List.of() : List.copyOf(links);
    }
}
