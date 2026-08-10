package br.com.puccomp.api.financial;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class FinancialEntryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("a listagem filtra por período e por tipo de lançamento")
    void shouldFilterEntriesByPeriodAndType() {
        String owner = ownerOf("EJ Filtros", "ej-filtros-fin", "dono-filtros@ej.dev");

        createEntry(owner, entry("2026-01-15", "420.00", "Patrocínio Empresa Beta", "INCOME", "Patrocínios"));
        createEntry(owner, entry("2026-01-20", "85.50", "Coffee break", "EXPENSE", "Eventos"));
        createEntry(owner, entry("2026-02-01", "300.00", "Mensalidades", "INCOME", "Mensalidades"));

        assertThat(descriptions(list(owner, "?from=2026-01-01&to=2026-01-31")))
                .containsExactlyInAnyOrder("Patrocínio Empresa Beta", "Coffee break");
        assertThat(descriptions(list(owner, "?type=INCOME")))
                .containsExactlyInAnyOrder("Patrocínio Empresa Beta", "Mensalidades");
        assertThat(descriptions(list(owner, "?from=2026-01-01&to=2026-01-31&type=INCOME")))
                .containsExactly("Patrocínio Empresa Beta");
    }

    @Test
    @DisplayName("período com início depois do fim é recusado")
    void shouldRejectPeriodStartingAfterItEnds() {
        String owner = ownerOf("EJ Período", "ej-periodo-fin", "dono-periodo@ej.dev");

        ResponseEntity<Map<String, Object>> res = exchange(owner, HttpMethod.GET,
                "/v1/financial/entries?from=2026-02-01&to=2026-01-01", null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("sem ordenação explícita, o extrato vem do lançamento mais recente para o mais antigo")
    void shouldListNewestEntriesFirstByDefault() {
        String owner = ownerOf("EJ Extrato", "ej-extrato-fin", "dono-extrato@ej.dev");

        createEntry(owner, entry("2026-05-10", "100.00", "Mais antigo", "INCOME", "Vendas"));
        createEntry(owner, entry("2026-05-20", "100.00", "Mesmo dia, registrado antes", "INCOME", "Vendas"));
        createEntry(owner, entry("2026-05-20", "100.00", "Mesmo dia, registrado depois", "INCOME", "Vendas"));

        assertThat(descriptions(list(owner, "")))
                .containsExactly("Mesmo dia, registrado depois", "Mesmo dia, registrado antes", "Mais antigo");
    }

    @Test
    @DisplayName("a atualização altera apenas os campos enviados e limpa o comprovante quando vem em branco")
    void shouldApplyOnlySentFieldsOnUpdate() {
        String owner = ownerOf("EJ Edição", "ej-edicao-fin", "dono-edicao@ej.dev");
        Map<String, Object> created = createEntry(owner, entryWithReceipt("2026-06-01", "420.00",
                "Patrocínio Empresa Beta", "INCOME", "Patrocínios", "https://example.com/recibos/beta"));

        ResponseEntity<Map<String, Object>> res = exchange(owner, HttpMethod.PATCH,
                "/v1/financial/entries/" + created.get("id"),
                Map.of("amount", new BigDecimal("500.00"), "category", "Parcerias", "receipt_url", ""));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> updated = res.getBody();
        assertThat(amountOf(updated)).isEqualTo("500.00");
        assertThat(updated.get("category")).isEqualTo("Parcerias");
        assertThat(updated.get("receipt_url")).isNull();
        assertThat(updated.get("description")).isEqualTo(created.get("description"));
        assertThat(updated.get("occurred_on")).isEqualTo(created.get("occurred_on"));
        assertThat(updated.get("type")).isEqualTo(created.get("type"));
        assertThat(updated.get("updated_at")).isNotEqualTo(created.get("updated_at"));
    }

    @Test
    @DisplayName("lançamentos financeiros são isolados entre EJs, inclusive na exclusão")
    void shouldIsolateEntriesBetweenTenants() {
        String alfa = ownerOf("EJ Alfa", "ej-alfa-fin", "dono-alfa-fin@ej.dev");
        String beta = ownerOf("EJ Beta", "ej-beta-fin", "dono-beta-fin@ej.dev");

        createEntry(alfa, entry("2026-03-01", "100.00", "Entrada Alfa", "INCOME", "Vendas"));
        Map<String, Object> betaEntry = createEntry(beta, entry("2026-03-02", "200.00", "Entrada Beta", "INCOME", "Vendas"));
        String betaEntryId = (String) betaEntry.get("id");

        assertThat(descriptions(list(alfa, ""))).containsExactly("Entrada Alfa");
        assertThat(getWithToken("/v1/financial/entries/" + betaEntryId, alfa).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(exchange(alfa, HttpMethod.DELETE, "/v1/financial/entries/" + betaEntryId, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(beta, HttpMethod.DELETE, "/v1/financial/entries/" + betaEntryId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(descriptions(list(beta, ""))).isEmpty();
    }

    @Test
    @DisplayName("ler o financeiro não dá direito de lançar")
    void shouldSeparateReadAndWritePermissions() {
        UUID tenant = seeder.seedTenant("EJ Permissões Financeiras", "ej-permissoes-fin");
        seeder.seedAccount(tenant, "dono-permissoes-fin@ej.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "membro-permissoes-fin@ej.dev", "senha123", Standing.STAFF);
        String owner = login("dono-permissoes-fin@ej.dev", "senha123");
        String member = login("membro-permissoes-fin@ej.dev", "senha123");

        assertThat(getWithToken("/v1/financial/entries", member).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        grantMemberPermissions(owner, memberId, List.of("financial:read"));

        assertThat(getWithToken("/v1/financial/entries", member).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(member, HttpMethod.POST, "/v1/financial/entries",
                entry("2026-04-02", "50.00", "Nova venda", "INCOME", "Vendas")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String ownerOf(String tenantName, String slug, String email) {
        UUID tenant = seeder.seedTenant(tenantName, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private Map<String, Object> createEntry(String token, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> res = exchange(token, HttpMethod.POST, "/v1/financial/entries", body);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private ResponseEntity<Map<String, Object>> list(String token, String query) {
        ResponseEntity<Map<String, Object>> res = exchange(token, HttpMethod.GET,
                "/v1/financial/entries" + query, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res;
    }

    private static Map<String, Object> entry(String occurredOn, String amount, String description,
                                             String type, String category) {
        return entryWithReceipt(occurredOn, amount, description, type, category, null);
    }

    private static Map<String, Object> entryWithReceipt(String occurredOn, String amount, String description,
                                                        String type, String category, String receiptUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("occurred_on", occurredOn);
        body.put("amount", new BigDecimal(amount));
        body.put("description", description);
        body.put("type", type);
        body.put("category", category);
        body.put("receipt_url", receiptUrl);
        return body;
    }

    private void grantMemberPermissions(String ownerToken, UUID memberId, List<String> permissions) {
        ResponseEntity<Map<String, Object>> res = exchange(ownerToken, HttpMethod.PUT,
                "/v1/authz/members/" + memberId + "/permissions", Map.of("permissions", permissions));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> exchange(String token, HttpMethod method,
                                                         String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<String> descriptions(ResponseEntity<Map<String, Object>> page) {
        return ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .map(entry -> (String) entry.get("description"))
                .toList();
    }

    private static String amountOf(Map<String, Object> entry) {
        return new BigDecimal(entry.get("amount").toString()).setScale(2).toPlainString();
    }
}
