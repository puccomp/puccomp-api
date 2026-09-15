package br.com.puccomp.api.financial;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class FinancialSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final String ENTRIES = "/v1/financial/entries";

    private static final String SUMMARY = ENTRIES + "/summary";

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("soma entradas, saídas e resultado do período, e distribui por categoria")
    void shouldSummarizeThePeriod() {
        String owner = ownerOf("EJ Resumo", "ej-resumo-fin", "dono-resumo@ej.dev");

        create(owner, "2026-03-05", "1000.00", FinancialEntryType.INCOME, "Patrocínios");
        create(owner, "2026-03-10", "500.00", FinancialEntryType.INCOME, "Mensalidades");
        create(owner, "2026-03-12", "200.00", FinancialEntryType.EXPENSE, "Eventos");
        create(owner, "2026-03-20", "300.00", FinancialEntryType.EXPENSE, "Eventos");
        create(owner, "2026-04-01", "999.00", FinancialEntryType.INCOME, "Fora do recorte");

        var summary = summary(owner, "?from=2026-03-01&to=2026-03-31");

        assertThat(amount(summary, "income", "value")).isEqualByComparingTo("1500.00");
        assertThat(amount(summary, "expense", "value")).isEqualByComparingTo("500.00");
        assertThat(amount(summary, "balance", "value")).isEqualByComparingTo("1000.00");
        assertThat(amount(summary, "entries", "value")).isEqualByComparingTo("4");

        assertThat(categories(summary, "expense_by_category")).containsExactly("Eventos");
        assertThat(categories(summary, "income_by_category"))
                .containsExactly("Patrocínios", "Mensalidades");
    }

    /** O recorte tem 31 dias, então o anterior vai de 29/01 a 28/02: janeiro fica fora dos dois. */
    @Test
    @DisplayName("compara com a janela anterior de mesma largura")
    void shouldCompareWithThePreviousWindow() {
        String owner = ownerOf("EJ Comparação", "ej-comparacao-fin", "dono-comparacao@ej.dev");

        create(owner, "2026-02-10", "400.00", FinancialEntryType.INCOME, "Patrocínios");
        create(owner, "2026-03-10", "1000.00", FinancialEntryType.INCOME, "Patrocínios");
        create(owner, "2026-01-05", "700.00", FinancialEntryType.INCOME, "Antigo demais");

        var summary = summary(owner, "?from=2026-03-01&to=2026-03-31");

        assertThat(amount(summary, "income", "value")).isEqualByComparingTo("1000.00");
        assertThat(amount(summary, "income", "previous")).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("sem os dois extremos não há janela anterior: previous é nulo, e não zero")
    void shouldLeavePreviousUndefinedWithoutBothEnds() {
        String owner = ownerOf("EJ Sem Janela", "ej-sem-janela-fin", "dono-sem-janela@ej.dev");

        create(owner, "2026-05-10", "250.00", FinancialEntryType.INCOME, "Patrocínios");

        var summary = summary(owner, "?from=2026-05-01");

        assertThat(amount(summary, "income", "value")).isEqualByComparingTo("250.00");
        assertThat(metric(summary, "income").get("previous")).isNull();
    }

    @Test
    @DisplayName("lançamento excluído sai do resumo e da listagem, nas mesmas contas")
    void shouldExcludeDiscardedEntries() {
        String owner = ownerOf("EJ Descarte", "ej-descarte-fin", "dono-descarte@ej.dev");

        create(owner, "2026-06-01", "100.00", FinancialEntryType.INCOME, "Vendas");
        UUID discarded = create(owner, "2026-06-02", "900.00", FinancialEntryType.INCOME, "Vendas");

        assertThat(amount(summary(owner, ""), "income", "value")).isEqualByComparingTo("1000.00");

        assertThat(delete(ENTRIES + "/" + discarded, owner, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(amount(summary(owner, ""), "income", "value")).isEqualByComparingTo("100.00");
        assertThat(amount(summary(owner, ""), "entries", "value")).isEqualByComparingTo("1");
        assertThat(get(ENTRIES + "/" + discarded, owner, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("o resumo exige financial:read, como a listagem")
    void shouldRequireReadPermission() {
        UUID tenant = seeder.seedTenant("EJ Resumo Permissão", "ej-resumo-permissao-fin");
        seeder.seedAccount(tenant, "dono-resumo-perm@ej.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenant, "membro-resumo-perm@ej.dev", "senha123", Standing.MEMBER);
        String member = login("membro-resumo-perm@ej.dev", "senha123");

        assertThat(get(SUMMARY, member, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("período com início depois do fim é recusado, como na listagem")
    void shouldRejectInvertedPeriod() {
        String owner = ownerOf("EJ Resumo Período", "ej-resumo-periodo-fin", "dono-resumo-periodo@ej.dev");

        assertThat(get(SUMMARY + "?from=2026-02-01&to=2026-01-01", owner, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> summary(String token, String query) {
        ResponseEntity<Map<String, Object>> res = get(SUMMARY + query, token,
                new ParameterizedTypeReference<Map<String, Object>>() { });
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metric(Map<String, Object> summary, String field) {
        return (Map<String, Object>) summary.get(field);
    }

    private static BigDecimal amount(Map<String, Object> summary, String field, String key) {
        return new BigDecimal(String.valueOf(metric(summary, field).get(key)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> categories(Map<String, Object> summary, String field) {
        return ((List<Map<String, Object>>) summary.get(field)).stream()
                .map(slice -> (String) ((Map<String, Object>) slice.get("key")).get("name"))
                .toList();
    }

    private UUID create(String token, String occurredOn, String amount, FinancialEntryType type,
                        String category) {
        var request = new FinancialEntryRequest(LocalDate.parse(occurredOn), new BigDecimal(amount),
                "Lançamento " + occurredOn, type, category, null);
        ResponseEntity<FinancialEntryResponse> res = post(ENTRIES, request, token, FinancialEntryResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody().id();
    }

    private String ownerOf(String tenantName, String slug, String email) {
        UUID tenant = seeder.seedTenant(tenantName, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }
}
