package br.com.puccomp.api.organization.members;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A migration do histórico roda sobre uma base que já tem EJs e membros — e é aí que ela pode
 * errar. Aqui o banco é levado até a versão anterior, povoado, e só então migrado: um baseline que
 * dependesse de {@code members.created_at} (que não existe) ou que inventasse admissões e saídas
 * para quem já estava lá falharia neste teste, não em produção.
 */
class MemberStatusHistoryMigrationTest {

    private static final String ANTES_DO_HISTORICO = "18";

    @Test
    @DisplayName("o baseline observa o estado de quem já existia, sem criar entradas nem saídas")
    void shouldBaselineExistingMembersWithoutInventingDates() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            migrateTo(postgres, ANTES_DO_HISTORICO);

            UUID tenant = UUID.randomUUID();
            UUID vazia = UUID.randomUUID();
            Map<String, UUID> membros = new LinkedHashMap<>();
            membros.put("ACTIVE", UUID.randomUUID());
            membros.put("ALUMNUS", UUID.randomUUID());
            membros.put("INACTIVE", UUID.randomUUID());

            try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
                statement.execute("""
                        insert into tenants (id, name, slug, status, created_at, updated_at) values
                          ('%s', 'EJ Antiga', 'ej-antiga', 'ACTIVE', now(), now()),
                          ('%s', 'EJ Sem Membros', 'ej-sem-membros', 'ACTIVE', now(), now())
                        """.formatted(tenant, vazia));
                UUID curso = UUID.randomUUID();
                statement.execute("""
                        insert into courses (id, tenant_id, name, active, created_at, updated_at)
                        values ('%s', '%s', 'Ciência da Computação', true, now(), now())
                        """.formatted(curso, tenant));
                membros.forEach((status, id) -> execute(statement, """
                        insert into members (id, tenant_id, name, standing, status, course_id)
                        values ('%s', '%s', 'Membro %s', 'MEMBER', '%s', '%s')
                        """.formatted(id, tenant, status, status, curso)));
            }

            migrateTo(postgres, "latest");

            try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
                // Toda EJ ganha marco de cobertura, inclusive a que ainda não tem membro nenhum.
                assertThat(scalar(statement, "select count(*) from organization_tracking")).isEqualTo(2);
                assertThat(scalar(statement,
                        "select count(*) from organization_tracking where tenant_id = '%s'".formatted(vazia)))
                        .isEqualTo(1);

                // Um evento por membro, e nenhum a mais: baseline não é entrada nem saída.
                assertThat(scalar(statement, "select count(*) from member_status_history")).isEqualTo(3);
                assertThat(scalar(statement,
                        "select count(*) from member_status_history where kind <> 'BASELINE'")).isZero();
                assertThat(scalar(statement,
                        "select count(*) from member_status_history where from_status is not null")).isZero();

                // O estado observado é o que o membro tinha — inclusive alumni e inativos antigos,
                // que continuam sendo observação e não saídas produzidas pela migration.
                membros.forEach((status, id) -> assertThat(text(statement,
                        "select to_status from member_status_history where member_id = '%s'".formatted(id)))
                        .isEqualTo(status));

                // A data do evento é o marco da EJ, e o histórico começa exatamente ali: nenhuma
                // data antiga foi inventada a partir de updated_at ou de aceite presumido.
                assertThat(scalar(statement, """
                        select count(*) from member_status_history h
                        join organization_tracking t on t.tenant_id = h.tenant_id
                        where h.occurred_at <> t.tracked_since
                        """)).isZero();
                assertThat(scalar(statement,
                        "select count(*) from member_status_history where sequence <> 1")).isZero();
            }
        }
    }

    private static void migrateTo(PostgreSQLContainer postgres, String target) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static Connection connect(PostgreSQLContainer postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
    }

    private static void execute(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    private static long scalar(Statement statement, String sql) {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    private static String text(Statement statement, String sql) {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        } catch (Exception exception) {
            throw new IllegalStateException(sql, exception);
        }
    }
}
