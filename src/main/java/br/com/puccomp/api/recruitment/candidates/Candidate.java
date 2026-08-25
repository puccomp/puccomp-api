package br.com.puccomp.api.recruitment.candidates;

import br.com.puccomp.api.shared.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.ArrayList;
import java.util.List;
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

    // Lista ordenada em vez de colunas fixas: cada EJ pede os links que quiser no formulário,
    // e a ordem em que a pessoa preencheu é a ordem em que a EJ vai ler.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "candidate_links", joinColumns = @JoinColumn(name = "candidate_id"))
    @OrderColumn(name = "link_order")
    @Column(name = "url", nullable = false, length = 500)
    @Builder.Default
    private List<String> links = new ArrayList<>();


    void changeFullName(String fullName) {
        this.fullName = fullName;
    }

    void changeEmail(String email) {
        this.email = email;
    }

    void changePhone(String phone) {
        this.phone = phone;
    }

    public List<String> getLinks() {
            return links == null ? List.of() : List.copyOf(links);
    }

    void changeLinks(List<String> novosLinks) {
        if (novosLinks != null && !novosLinks.isEmpty()) {
            this.links.clear();
            this.links.addAll(novosLinks.stream().limit(5).toList());
        }
    }
}
