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
    @DisplayName("CRUD de lançamentos respeita filtros por período e tipo")
    void shouldManageEntriesWithPeriodAndTypeFilters() {
        UUID tenant = seeder.seedTenant("EJ Financeira", "ej-financeira");
        seeder.seedAccount(tenant, "dono-financeiro@ej.dev", "senha123", Standing.OWNER);
        String owner = login("dono-financeiro@ej.dev", "senha123");

        Map<String, Object> income = createEntry(owner, entry("2026-01-15", "420.00",
                "Patrocínio Empresa Beta", "INCOME", "Patrocínios", "https://example.com/recibos/beta"));
        createEntry(owner, entry("2026-01-20", "85.50",
                "Coffee break", "OUTCOME", "Eventos", null));
        createEntry(owner, entry("2026-02-01", "300.00",
                "Mensalidades", "INCOME", "Mensalidades", null));

        ResponseEntity<Map<String, Object>> filtered = exchange(owner, HttpMethod.GET,
                "/v1/financial/entries?from=2026-01-01&to=2026-01-31&type=INCOME", null);

        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = content(filtered);
        assertThat(content).hasSize(1);
        assertThat(content.getFirst().get("description")).isEqualTo("Patrocínio Empresa Beta");

        String id = (String) income.get("id");
        ResponseEntity<Map<String, Object>> updated = exchange(owner, HttpMethod.PATCH,
                "/v1/financial/entries/" + id,
                Map.of("value", "500.00", "category", "Parcerias", "receipt_url", ""));

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updated.getBody().get("value")).doubleValue()).isEqualTo(500.00);
        assertThat(updated.getBody().get("category")).isEqualTo("Parcerias");
        assertThat(updated.getBody().get("receipt_url")).isNull();

        ResponseEntity<Map<String, Object>> invalidPeriod = exchange(owner, HttpMethod.GET,
                "/v1/financial/entries?from=2026-02-01&to=2026-01-01", null);
        assertThat(invalidPeriod.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> deleted = exchange(owner, HttpMethod.DELETE,
                "/v1/financial/entries/" + id, null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getWithToken("/v1/financial/entries/" + id, owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("lançamentos financeiros são isolados entre tenants")
    void shouldIsolateEntriesBetweenTenants() {
        UUID tenantA = seeder.seedTenant("EJ Alfa", "ej-alfa-fin");
        UUID tenantB = seeder.seedTenant("EJ Beta", "ej-beta-fin");
        seeder.seedAccount(tenantA, "dono-alfa-fin@ej.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenantB, "dono-beta-fin@ej.dev", "senha123", Standing.OWNER);
        String tokenA = login("dono-alfa-fin@ej.dev", "senha123");
        String tokenB = login("dono-beta-fin@ej.dev", "senha123");

        createEntry(tokenA, entry("2026-03-01", "100.00", "Entrada Alfa", "INCOME", "Vendas", null));
        Map<String, Object> betaEntry = createEntry(tokenB,
                entry("2026-03-02", "200.00", "Entrada Beta", "INCOME", "Vendas", null));

        ResponseEntity<String> listA = getWithToken("/v1/financial/entries", tokenA);

        assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listA.getBody()).contains("Entrada Alfa").doesNotContain("Entrada Beta");
        assertThat(getWithToken("/v1/financial/entries/" + betaEntry.get("id"), tokenA).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("permissões financeiras separam leitura e escrita")
    void shouldEnforceFinancialPermissions() {
        UUID tenant = seeder.seedTenant("EJ Permissões Financeiras", "ej-permissoes-fin");
        seeder.seedAccount(tenant, "dono-permissoes-fin@ej.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "membro-permissoes-fin@ej.dev", "senha123", Standing.MEMBER);
        String owner = login("dono-permissoes-fin@ej.dev", "senha123");
        String member = login("membro-permissoes-fin@ej.dev", "senha123");
        createEntry(owner, entry("2026-04-01", "150.00", "Venda", "INCOME", "Vendas", null));

        assertThat(getWithToken("/v1/financial/entries", member).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        grantMemberPermissions(owner, memberId, List.of("financial:read"));
        assertThat(getWithToken("/v1/financial/entries", member).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> forbiddenWrite = exchange(member, HttpMethod.POST,
                "/v1/financial/entries",
                entry("2026-04-02", "50.00", "Nova venda", "INCOME", "Vendas", null));
        assertThat(forbiddenWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Map<String, Object> createEntry(String token, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> res = exchange(token, HttpMethod.POST, "/v1/financial/entries", body);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    private static Map<String, Object> entry(String date, String value, String description,
                                            String type, String category, String receiptUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date);
        body.put("value", new BigDecimal(value));
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
    private static List<Map<String, Object>> content(ResponseEntity<Map<String, Object>> page) {
        return (List<Map<String, Object>>) page.getBody().get("content");
    }
}
