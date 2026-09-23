package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class MemberRepositoryN1Test extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberService memberService;

    @Autowired
    EntityManager entityManager;

    Statistics statistics;

    UUID tenantId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantId = seeder.seedTenant("EJ N1 Membros", "ej-n1-membros-" + suffix);

        // O e-mail da conta é único no banco inteiro, e o fixture roda uma vez por teste da classe.
        for (int i = 0; i < 5; i++) {
            seeder.seedCourse(tenantId, "Curso " + i);
            seeder.seedAccount(tenantId, "membro" + i + "-" + suffix + "@n1.com", "senha123",
                    Standing.MEMBER);
        }

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    private static MemberFilter noFilter() {
        return new MemberFilter(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("listagem de membros traz curso, cargo e diretoria na própria query, sem ida extra ao banco")
    void shouldNotGenerateAdditionalQueryPerCourse() {
        Pageable pageable = PageRequest.of(0, 20);

        // sem o tenant no contexto o filtro do @TenantId descarta tudo, e a contagem
        // de queries passaria a valer sobre uma lista vazia.
        TenantContext.set(tenantId);
        try {
            // Pelo mesmo caminho da listagem: o grafo de fetch precisa sobreviver à Specification.
            var members = memberRepository
                    .findAll(MemberSpecs.matching(noFilter()), pageable).getContent();
            assertThat(members).hasSize(5);
            members.forEach(member -> member.getCourse().getName());

            assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(2);
            // getQueryExecutionCount ignora carga preguiçosa de associação; quem denuncia o
            // N+1 é o entity fetch, disparado quando o curso fica de fora do grafo.
            assertThat(statistics.getEntityFetchCount()).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a data de entrada da página inteira sai de uma consulta só, não de uma por membro")
    void shouldResolveJoinDatesInASingleQuery() {
        TenantContext.set(tenantId);
        try {
            var page = memberService.findAll(noFilter(), PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(5);
            assertThat(page.getContent()).allSatisfy(member ->
                    assertThat(member.joinedAt()).isNotNull());
            // listagem, contagem da página e histórico. Uma consulta por membro seria 5 a mais.
            assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(3);
        } finally {
            TenantContext.clear();
        }
    }
}