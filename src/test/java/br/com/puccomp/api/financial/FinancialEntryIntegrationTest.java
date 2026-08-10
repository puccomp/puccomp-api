package br.com.puccomp.api.financial;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class FinancialEntryIntegrationTest extends AbstractIntegrationTest {

    private static final String ENTRIES = "/v1/financial/entries";

    @Autowired
    private TestSeeder seeder;

    /** Só o que a listagem precisa devolver; o resto do envelope de paginação é ignorado pelo Jackson. */
    private record EntryPage(List<FinancialEntryResponse> content) {
    }

    @Test
    @DisplayName("a listagem filtra por período e por tipo de lançamento")
    void shouldFilterEntriesByPeriodAndType() {
        String owner = ownerOf("EJ Filtros", "ej-filtros-fin", "dono-filtros@ej.dev");

        createEntry(owner, entry("2026-01-15", "420.00", "Patrocínio Empresa Beta", FinancialEntryType.INCOME));
        createEntry(owner, entry("2026-01-20", "85.50", "Coffee break", FinancialEntryType.EXPENSE));
        createEntry(owner, entry("2026-02-01", "300.00", "Mensalidades", FinancialEntryType.INCOME));

        assertThat(descriptions(owner, "?from=2026-01-01&to=2026-01-31"))
                .containsExactlyInAnyOrder("Patrocínio Empresa Beta", "Coffee break");
        assertThat(descriptions(owner, "?type=INCOME"))
                .containsExactlyInAnyOrder("Patrocínio Empresa Beta", "Mensalidades");
        assertThat(descriptions(owner, "?from=2026-01-01&to=2026-01-31&type=INCOME"))
                .containsExactly("Patrocínio Empresa Beta");
    }

    @Test
    @DisplayName("período com início depois do fim é recusado")
    void shouldRejectPeriodStartingAfterItEnds() {
        String owner = ownerOf("EJ Período", "ej-periodo-fin", "dono-periodo@ej.dev");

        ResponseEntity<ErrorResponse> res = get(ENTRIES + "?from=2026-02-01&to=2026-01-01", owner, ErrorResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("sem ordenação explícita, o extrato vem do lançamento mais recente para o mais antigo")
    void shouldListNewestEntriesFirstByDefault() {
        String owner = ownerOf("EJ Extrato", "ej-extrato-fin", "dono-extrato@ej.dev");

        createEntry(owner, entry("2026-05-10", "100.00", "Mais antigo", FinancialEntryType.INCOME));
        createEntry(owner, entry("2026-05-20", "100.00", "Mesmo dia, registrado antes", FinancialEntryType.INCOME));
        createEntry(owner, entry("2026-05-20", "100.00", "Mesmo dia, registrado depois", FinancialEntryType.INCOME));

        assertThat(descriptions(owner, ""))
                .containsExactly("Mesmo dia, registrado depois", "Mesmo dia, registrado antes", "Mais antigo");
    }

    @Test
    @DisplayName("a atualização altera apenas os campos enviados e limpa o comprovante quando vem em branco")
    void shouldApplyOnlySentFieldsOnUpdate() {
        String owner = ownerOf("EJ Edição", "ej-edicao-fin", "dono-edicao@ej.dev");
        FinancialEntryResponse created = createEntry(owner, new FinancialEntryRequest(
                LocalDate.parse("2026-06-01"), new BigDecimal("420.00"), "Patrocínio Empresa Beta",
                FinancialEntryType.INCOME, "Patrocínios", "https://example.com/recibos/beta"));

        FinancialEntryUpdateRequest onlyAmountAndCategory = new FinancialEntryUpdateRequest(
                null, new BigDecimal("500.00"), null, null, "Parcerias", "");
        ResponseEntity<FinancialEntryResponse> res = patch(ENTRIES + "/" + created.id(),
                onlyAmountAndCategory, owner, FinancialEntryResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        FinancialEntryResponse updated = res.getBody();
        assertThat(updated.amount()).isEqualByComparingTo("500.00");
        assertThat(updated.category()).isEqualTo("Parcerias");
        assertThat(updated.receiptUrl()).isNull();
        assertThat(updated.description()).isEqualTo(created.description());
        assertThat(updated.occurredOn()).isEqualTo(created.occurredOn());
        assertThat(updated.type()).isEqualTo(created.type());
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
    }

    @Test
    @DisplayName("lançamentos financeiros são isolados entre EJs, inclusive na exclusão")
    void shouldIsolateEntriesBetweenTenants() {
        String alfa = ownerOf("EJ Alfa", "ej-alfa-fin", "dono-alfa-fin@ej.dev");
        String beta = ownerOf("EJ Beta", "ej-beta-fin", "dono-beta-fin@ej.dev");

        createEntry(alfa, entry("2026-03-01", "100.00", "Entrada Alfa", FinancialEntryType.INCOME));
        UUID betaEntryId = createEntry(beta, entry("2026-03-02", "200.00", "Entrada Beta", FinancialEntryType.INCOME)).id();

        assertThat(descriptions(alfa, "")).containsExactly("Entrada Alfa");
        assertThat(get(ENTRIES + "/" + betaEntryId, alfa, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(delete(ENTRIES + "/" + betaEntryId, alfa, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(ENTRIES + "/" + betaEntryId, beta, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(descriptions(beta, "")).isEmpty();
    }

    @Test
    @DisplayName("ler o financeiro não dá direito de lançar")
    void shouldSeparateReadAndWritePermissions() {
        UUID tenant = seeder.seedTenant("EJ Permissões Financeiras", "ej-permissoes-fin");
        seeder.seedAccount(tenant, "dono-permissoes-fin@ej.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "membro-permissoes-fin@ej.dev", "senha123", Standing.STAFF);
        String owner = login("dono-permissoes-fin@ej.dev", "senha123");
        String member = login("membro-permissoes-fin@ej.dev", "senha123");

        assertThat(get(ENTRIES, member, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        grantMemberPermissions(owner, memberId, List.of("financial:read"));

        assertThat(get(ENTRIES, member, EntryPage.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(ENTRIES, entry("2026-04-02", "50.00", "Nova venda", FinancialEntryType.INCOME),
                member, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String ownerOf(String tenantName, String slug, String email) {
        UUID tenant = seeder.seedTenant(tenantName, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private FinancialEntryResponse createEntry(String token, FinancialEntryRequest request) {
        ResponseEntity<FinancialEntryResponse> res = post(ENTRIES, request, token, FinancialEntryResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private static FinancialEntryRequest entry(String occurredOn, String amount, String description,
                                               FinancialEntryType type) {
        return new FinancialEntryRequest(LocalDate.parse(occurredOn), new BigDecimal(amount),
                description, type, "Vendas", null);
    }

    private List<String> descriptions(String token, String query) {
        ResponseEntity<EntryPage> res = get(ENTRIES + query, token, EntryPage.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().content().stream().map(FinancialEntryResponse::description).toList();
    }

    private void grantMemberPermissions(String ownerToken, UUID memberId, List<String> permissions) {
        ResponseEntity<String> res = put("/v1/authz/members/" + memberId + "/permissions",
                Map.of("permissions", permissions), ownerToken, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
