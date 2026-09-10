package br.com.puccomp.api.dev;

import br.com.puccomp.api.shared.token.TokenSecrets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Sobe duas EJs de demonstração com anos de história.
 *
 * <p>A massa antiga tinha duas contas e um cargo: bastava para autenticar, e não para ver nada.
 * Resumo do quadro, ocupação de vagas, lacunas da estrutura, funil de recrutamento, relatório
 * histórico e fluxo de caixa só dizem alguma coisa sobre uma EJ que já viveu — com gente que
 * entrou, saiu, voltou, cargos cheios, cargos vagos e uma diretoria sem ninguém.
 *
 * <p>Os módulos aparecem ligados, não lado a lado: os aprovados de um processo seletivo viram os
 * membros do mês seguinte, com o mesmo nome e o mesmo curso da inscrição; os que ainda não
 * aceitaram viram convite pendente; e a despesa de divulgação de cada processo cai no mês em que
 * ele abriu. Massa com módulos desconexos passa em todo teste e não revela nenhum erro de junção.
 *
 * <p>As contas cobrem os níveis de acesso que os endpoints distinguem: o dono vê tudo, a diretoria
 * de Gente e Gestão vê o contexto organizacional inteiro, o desenvolvedor tem apenas
 * {@code members:read} — e recebe {@code null} nos blocos de capacidade —, o membro sem cargo não
 * tem permissão nenhuma, e uma conta pertence às duas EJs ao mesmo tempo.
 *
 * <p>Os identificadores são derivados dos nomes, e não sorteados: reinstalar a base mantém os ids
 * que estão nas variáveis da collection do Bruno.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
class DevDataSeeder implements ApplicationRunner {

    private static final ZoneId EJ = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Value("${puccomp.files.enabled:false}")
    private boolean filesEnabled;

    /** Sorteio determinístico: a mesma massa em toda máquina, sem virar dado escrito à mão. */
    private final Random random = new Random(20260907L);

    private final List<String> credenciais = new ArrayList<>();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("select count(*) from tenants", Long.class) > 0) return;

        DevFixture.EJS.forEach(this::semear);
        contaEmDuasEjs();

        log.info("""
                [dev seed] {} EJs, {} membros, {} cargos, {} inscrições, {} convites, \
                {} lançamentos e {} eventos de vínculo.
                [dev seed] Contas (senha entre parênteses): {}
                [dev seed] Currículos {}.""",
                conta("tenants"), conta("members"), conta("roles"), conta("candidate_applications"),
                conta("invitations"), conta("financial_entries"), conta("member_status_history"),
                String.join("; ", credenciais),
                filesEnabled ? "anexados" : "não anexados: puccomp.files.enabled está desligado, "
                        + "e a listagem de inscrições falharia ao assinar a URL de download");
    }

    /** O estado de uma EJ enquanto ela é montada: os ids que as tabelas seguintes precisam. */
    private static final class Contexto {
        private final DevFixture.Ej ej;
        private final UUID tenantId;
        private final Instant inicio;
        private final Map<String, UUID> cursos = new LinkedHashMap<>();
        private final Map<String, UUID> diretorias = new HashMap<>();
        private final Map<String, UUID> cargos = new HashMap<>();
        private final Map<String, UUID> membros = new LinkedHashMap<>();

        private Contexto(DevFixture.Ej ej, UUID tenantId, Instant inicio) {
            this.ej = ej;
            this.tenantId = tenantId;
            this.inicio = inicio;
        }
    }

    private void semear(DevFixture.Ej ej) {
        var ctx = new Contexto(ej, id("tenant:" + ej.chave()), mes(ej, 0));

        tenant(ctx);
        catalogo(ctx);
        List<Aprovado> aprovados = recrutamento(ctx);
        quadro(ctx, aprovados);
        contas(ctx);
        convites(ctx, aprovados);
        tokens(ctx);
        caixa(ctx);
    }

    // ------------------------------------------------------------------ identidade e catálogo

    private void tenant(Contexto ctx) {
        jdbc.update("""
                insert into tenants (id, name, slug, status, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', ?, ?)
                """, ctx.tenantId, ctx.ej.nome(), ctx.ej.slug(), ts(ctx.inicio), ts(ctx.inicio));

        // Marco de cobertura no passado: sem ele o relatório histórico responderia "não sei" a
        // tudo, que é o correto para uma EJ recém-criada e inútil para demonstrar o endpoint.
        jdbc.update("insert into organization_tracking (id, tenant_id, tracked_since) values (?, ?, ?)",
                id("tracking:" + ctx.ej.chave()), ctx.tenantId, ts(ctx.inicio));
    }

    private void catalogo(Contexto ctx) {
        ctx.ej.cursos().forEach(curso -> {
            UUID cursoId = id("curso:" + ctx.ej.chave() + ":" + curso.nome());
            ctx.cursos.put(curso.nome(), cursoId);
            jdbc.update("""
                    insert into courses (id, tenant_id, name, active, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    """, cursoId, ctx.tenantId, curso.nome(), curso.ativo(),
                    ts(ctx.inicio), ts(ctx.inicio));
        });

        ctx.ej.diretorias().forEach(diretoria -> {
            UUID diretoriaId = id("diretoria:" + ctx.ej.chave() + ":" + diretoria.chave());
            ctx.diretorias.put(diretoria.chave(), diretoriaId);
            jdbc.update("""
                    insert into departments (id, tenant_id, name, description, active, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, diretoriaId, ctx.tenantId, diretoria.nome(), diretoria.descricao(),
                    diretoria.ativa(), ts(ctx.inicio), ts(ctx.inicio));
        });

        ctx.ej.cargos().forEach(cargo -> {
            UUID cargoId = id("cargo:" + ctx.ej.chave() + ":" + cargo.chave());
            ctx.cargos.put(cargo.chave(), cargoId);
            jdbc.update("""
                    insert into roles (id, tenant_id, name, description, department_id, max_seats,
                        active, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, cargoId, ctx.tenantId, cargo.nome(), cargo.descricao(),
                    cargo.diretoria() == null ? null : ctx.diretorias.get(cargo.diretoria()),
                    cargo.vagas(), cargo.ativo(), ts(ctx.inicio), ts(ctx.inicio));

            cargo.permissoes().forEach(permissao -> jdbc.update("""
                    insert into role_permissions (id, tenant_id, role_id, permission, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    """, id("perm:" + ctx.ej.chave() + ":" + cargo.chave() + ":" + permissao),
                    ctx.tenantId, cargoId, codigo(permissao), ts(ctx.inicio), ts(ctx.inicio)));
        });
    }

    // ------------------------------------------------------------------ quadro

    /**
     * Cada pessoa vira o membro de hoje e a sequência de eventos que a trouxe até aqui. Os dois
     * saem do mesmo lugar de propósito: é o histórico que o relatório temporal lê, e a projeção
     * precisa concordar com ele.
     */
    private void quadro(Contexto ctx, List<Aprovado> aprovados) {
        List<Object[]> pessoas = new ArrayList<>();
        List<Object[]> eventos = new ArrayList<>();

        List<DevFixture.Pessoa> todas = new ArrayList<>(ctx.ej.nucleo());
        todas.addAll(turmas(ctx, aprovados));

        for (DevFixture.Pessoa pessoa : todas) {
            UUID membroId = id("membro:" + ctx.ej.chave() + ":" + pessoa.nome());
            ctx.membros.put(pessoa.nome(), membroId);

            UUID cargoId = pessoa.cargo() == null ? null : ctx.cargos.get(pessoa.cargo());
            String diretoria = pessoa.cargo() == null ? pessoa.diretoria()
                    : ctx.ej.cargos().stream().filter(c -> c.chave().equals(pessoa.cargo()))
                    .findFirst().orElseThrow().diretoria();

            pessoas.add(new Object[] {membroId, ctx.tenantId, pessoa.nome(),
                    ctx.ej.nucleo().getFirst().nome().equals(pessoa.nome()) ? "OWNER" : "MEMBER",
                    pessoa.statusFinal(), ctx.cursos.get(nomeDoCurso(ctx, pessoa.curso())), cargoId,
                    diretoria == null ? null : ctx.diretorias.get(diretoria)});

            long sequencia = 1;
            eventos.add(evento(ctx, membroId, sequencia++, "CREATED", null, "ACTIVE",
                    mes(ctx.ej, pessoa.entrada())));
            if (pessoa.saida() != null)
                eventos.add(evento(ctx, membroId, sequencia++, "STATUS_CHANGED", "ACTIVE",
                        pessoa.statusApos(), mes(ctx.ej, pessoa.saida())));
            if (pessoa.retorno() != null)
                eventos.add(evento(ctx, membroId, sequencia, "STATUS_CHANGED", pessoa.statusApos(),
                        "ACTIVE", mes(ctx.ej, pessoa.retorno())));
        }

        jdbc.batchUpdate("""
                insert into members (id, tenant_id, name, standing, status, course_id, role_id, department_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, pessoas);
        jdbc.batchUpdate("""
                insert into member_status_history
                    (id, tenant_id, member_id, sequence, kind, from_status, to_status, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, eventos);
    }

    /**
     * Os aprovados de cada processo viram a turma admitida no mês seguinte ao encerramento — o
     * mesmo nome e o mesmo curso da inscrição. Um em cada quatro sai depois de alguns semestres,
     * que é o que dá turnover e permanência ao relatório em vez de uma curva sempre crescente.
     */
    private List<DevFixture.Pessoa> turmas(Contexto ctx, List<Aprovado> aprovados) {
        List<DevFixture.Pessoa> turma = new ArrayList<>();
        List<String> alocacao = ctx.ej.cargosDeTurma();

        for (int i = 0; i < aprovados.size(); i++) {
            Aprovado aprovado = aprovados.get(i);
            if (aprovado.convidado()) continue;

            String cargo = alocacao.get(i % alocacao.size());
            int entrada = aprovado.mesDeAdmissao();
            boolean sai = i % 4 == 3;
            int saida = Math.min(entrada + 9 + (i % 4) * 3, ctx.ej.meses() - 1);

            turma.add(sai && saida > entrada
                    ? DevFixture.Pessoa.saiu(aprovado.nome(), aprovado.curso(), cargo, entrada,
                            saida, i % 8 == 3 ? "ALUMNUS" : "INACTIVE")
                    : DevFixture.Pessoa.ativa(aprovado.nome(), aprovado.curso(), cargo, entrada));
        }
        return turma;
    }

    private Object[] evento(Contexto ctx, UUID membroId, long sequencia, String tipo, String de,
                            String para, Instant quando) {
        return new Object[] {id("evento:" + membroId + ":" + sequencia), ctx.tenantId, membroId,
                sequencia, tipo, de, para, ts(quando)};
    }

    // ------------------------------------------------------------------ contas e credenciais

    private void contas(Contexto ctx) {
        List<String[]> perfis = ctx.ej.chave().equals("comp")
                ? List.of(
                        new String[] {"dono", "Dono@123", "Ana Beatriz Ramos"},
                        new String[] {"diretora", "Diretor@123", "Mariana Alves Costa"},
                        new String[] {"rh", "RH@123", "Juliana Ferreira Braz"},
                        new String[] {"financeiro", "Financ@123", "Bruno Henrique Dias"},
                        new String[] {"dev", "Dev@12345", "Isabela Martins Leal"},
                        new String[] {"membro", "Membro@123", "Melissa Tanaka Okada"})
                : List.of(
                        new String[] {"dono", "Dono@123", "Helena Castro Vidal"},
                        new String[] {"analytics", "Analytics@123", "Otávio Menezes Lira"});

        for (String[] perfil : perfis) {
            String email = perfil[0] + "@" + ctx.ej.dominio();
            criarConta(ctx, email, perfil[1], perfil[2]);
            credenciais.add("%s (%s)".formatted(email, perfil[1]));
        }

        // Grant individual: soma-se ao do cargo, e é o caso que a precedência de permissões existe
        // para resolver. Esta desenvolvedora enxerga o caixa sem ser da diretoria financeira.
        if (ctx.ej.chave().equals("comp"))
            jdbc.update("""
                    insert into member_permissions (id, tenant_id, member_id, permission, created_at, updated_at)
                    values (?, ?, ?, 'FINANCIAL_READ', ?, ?)
                    """, id("mperm:financial:" + ctx.ej.chave()), ctx.tenantId,
                    ctx.membros.get("Isabela Martins Leal"), ts(ctx.inicio), ts(ctx.inicio));
    }

    private UUID criarConta(Contexto ctx, String email, String senha, String nomeDoMembro) {
        UUID contaId = id("conta:" + email);
        jdbc.update("""
                insert into accounts (id, email, password_hash, status, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', ?, ?)
                """, contaId, email, passwordEncoder.encode(senha), ts(ctx.inicio), ts(ctx.inicio));
        jdbc.update("update members set account_id = ? where id = ?",
                contaId, ctx.membros.get(nomeDoMembro));
        return contaId;
    }

    /**
     * Uma conta com vínculo nas duas EJs. É o caso que o login resolve pedindo
     * {@code organization_id}, e que nenhum ambiente de um tenant só consegue exercitar.
     */
    private void contaEmDuasEjs() {
        String email = "consultor@puccomp.dev";
        UUID contaId = id("conta:" + email);
        jdbc.update("""
                insert into accounts (id, email, password_hash, status, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', now(), now())
                """, contaId, email, passwordEncoder.encode("Consultor@123"));

        DevFixture.EJS.forEach(ej -> {
            UUID tenantId = id("tenant:" + ej.chave());
            UUID membroId = id("membro-duplo:" + ej.chave());
            UUID curso = id("curso:" + ej.chave() + ":" + ej.cursos().getFirst().nome());
            jdbc.update("""
                    insert into members (id, tenant_id, account_id, name, standing, status, course_id)
                    values (?, ?, ?, 'Sérgio Kubota Andrade', 'MEMBER', 'ACTIVE', ?)
                    """, membroId, tenantId, contaId, curso);
            jdbc.update("""
                    insert into member_status_history
                        (id, tenant_id, member_id, sequence, kind, to_status, occurred_at)
                    values (?, ?, ?, 1, 'CREATED', 'ACTIVE', ?)
                    """, id("evento-duplo:" + ej.chave()), tenantId, membroId,
                    ts(mes(ej, ej.meses() - 2)));
        });

        credenciais.add("%s (Consultor@123, vinculado às duas EJs — o login pede organization_id)"
                .formatted(email));
    }

    // ------------------------------------------------------------------ convites e tokens

    /**
     * Os quatro estados possíveis de um convite, ancorados no fluxo real: os pendentes são os
     * aprovados do último processo que ainda não aceitaram, o aceito é de alguém que já é membro.
     */
    private void convites(Contexto ctx, List<Aprovado> aprovados) {
        UUID autor = id("conta:dono@" + ctx.ej.dominio());
        Instant agora = Instant.now();
        List<Object[]> linhas = new ArrayList<>();

        int i = 0;
        for (Aprovado aprovado : aprovados) {
            if (!aprovado.convidado()) continue;
            linhas.add(convite(ctx, "pendente-" + i++, aprovado.email(),
                    ctx.ej.cargoDeEntrada(), autor,
                    agora.plus(Duration.ofDays(5)), null, null));
        }

        String dominio = ctx.ej.dominio();
        // Expirado: continua na listagem, mas o aceite recusa — e o motivo é a data, não a ausência.
        linhas.add(convite(ctx, "expirado", "expirado@" + dominio, ctx.ej.cargoDeEntrada(), autor,
                agora.minus(Duration.ofDays(3)), null, null));
        // Revogado antes de ser usado.
        linhas.add(convite(ctx, "revogado", "revogado@" + dominio, ctx.ej.cargoDeEntrada(), autor,
                agora.plus(Duration.ofDays(9)), null, agora.minus(Duration.ofDays(1))));
        // Aceito: é por onde entrou uma pessoa que hoje é membro de verdade nesta EJ.
        String jaEntrou = ctx.ej.chave().equals("comp") ? "membro@" + dominio : "analytics@" + dominio;
        linhas.add(convite(ctx, "aceito", jaEntrou, ctx.ej.cargoDeEntrada(), autor,
                ctx.inicio.plus(Duration.ofDays(30)), ctx.inicio.plus(Duration.ofDays(2)), null));

        jdbc.batchUpdate("""
                insert into invitations (id, tenant_id, email, standing, role_id, token_hash,
                    token_prefix, expires_at, accepted_at, revoked_at, created_by_account_id,
                    created_at, updated_at)
                values (?, ?, ?, 'MEMBER', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, linhas);
    }

    private Object[] convite(Contexto ctx, String chave, String email, String cargo, UUID autor,
                             Instant expira, Instant aceito, Instant revogado) {
        // O segredo é derivado da chave: o convite pendente do log é aceitável de verdade, e
        // reinstalar a base não invalida o link que ficou aberto numa aba.
        String bruto = "inv_dev_" + ctx.ej.chave() + "_" + chave;
        Instant criado = expira.minus(Duration.ofDays(14));
        if (chave.startsWith("pendente"))
            credenciais.add("convite %s → token %s".formatted(email, bruto));
        return new Object[] {id("convite:" + ctx.ej.chave() + ":" + chave), ctx.tenantId, email,
                ctx.cargos.get(cargo), TokenSecrets.sha256Hex(bruto), bruto.substring(0, 12),
                ts(expira), aceito == null ? null : ts(aceito),
                revogado == null ? null : ts(revogado), autor, ts(criado), ts(criado)};
    }

    /** Um token de máquina em cada estado que a listagem distingue: ativo, expirado e revogado. */
    private void tokens(Contexto ctx) {
        UUID conta = id("conta:dono@" + ctx.ej.dominio());
        Instant agora = Instant.now();
        List<Object[]> linhas = List.of(
                pat(ctx, conta, "integracao-site", "recruitment:read",
                        agora.plus(Duration.ofDays(180)), agora.minus(Duration.ofDays(2)), null),
                pat(ctx, conta, "relatorio-mensal", "members:read,financial:read",
                        agora.minus(Duration.ofDays(10)), agora.minus(Duration.ofDays(40)), null),
                pat(ctx, conta, "script-antigo", null, null, null, agora.minus(Duration.ofDays(60))));

        jdbc.batchUpdate("""
                insert into personal_access_tokens (id, tenant_id, account_id, name, token_hash,
                    token_prefix, scopes, expires_at, last_used_at, revoked_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, linhas);
    }

    private Object[] pat(Contexto ctx, UUID conta, String nome, String escopos, Instant expira,
                         Instant usado, Instant revogado) {
        String bruto = "pat_dev_" + ctx.ej.chave() + "_" + nome;
        Instant criado = ctx.inicio.plus(Duration.ofDays(60));
        if (revogado == null && (expira == null || expira.isAfter(Instant.now())))
            credenciais.add("PAT %s → %s".formatted(nome, bruto));
        return new Object[] {id("pat:" + ctx.ej.chave() + ":" + nome), ctx.tenantId, conta, nome,
                TokenSecrets.sha256Hex(bruto), bruto.substring(0, 12), escopos,
                expira == null ? null : ts(expira), usado == null ? null : ts(usado),
                revogado == null ? null : ts(revogado), ts(criado), ts(criado)};
    }

    // ------------------------------------------------------------------ auxiliares

    private String nomeDoCurso(Contexto ctx, int indice) {
        return ctx.ej.cursos().get(Math.min(indice, ctx.ej.cursos().size() - 1)).nome();
    }

    private long conta(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Long.class);
    }

    /** O mês {@code offset}, contado do início do rastreamento daquela EJ. */
    private static Instant mes(DevFixture.Ej ej, int offset) {
        return LocalDate.now(EJ).withDayOfMonth(1).minusMonths(ej.meses()).plusMonths(offset)
                .atTime(9, 30).atZone(EJ).toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    /** Id derivado do nome: reinstalar a base não invalida as variáveis salvas na collection. */
    private static UUID id(String chave) {
        return UUID.nameUUIDFromBytes(("puccomp:dev:" + chave).getBytes(StandardCharsets.UTF_8));
    }

    private static String codigo(String permissao) {
        return permissao.replace(':', '_').toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ recrutamento

    /** Um inscrito que virou membro — ou que ainda tem um convite aberto esperando o aceite. */
    private record Aprovado(String nome, String email, int curso, int mesDeAdmissao, boolean convidado) { }

    /**
     * Os processos da EJ e, deles, as turmas que entraram. A curva de chegada é concentrada no fim
     * do prazo — que é o que {@code peak_day} e {@code last_day_share} existem para mostrar, e o
     * que uma massa uniformemente distribuída esconderia.
     */
    private List<Aprovado> recrutamento(Contexto ctx) {
        List<Aprovado> aprovados = new ArrayList<>();

        for (DevFixture.Processo processo : ctx.ej.processos()) {
            Instant abre = mes(ctx.ej, processo.mesAbertura());
            Instant fecha = fimDoDia(abre.plus(Duration.ofDays(processo.semanasDePrazo() * 7L)));
            aprovados.addAll(publicar(ctx, processo.chave(), tituloDe(ctx.ej, processo.mesAbertura()),
                    processo.status(), abre, fecha, processo, fecha, true));
        }

        if (ctx.ej.processoAberto()) {
            // Aberto de verdade: a janela é o que governa quem aceita inscrição, então ela precisa
            // alcançar o presente para o endpoint público funcionar no ambiente de desenvolvimento.
            Instant agora = Instant.now();
            Instant abre = agora.minus(Duration.ofDays(11));
            var emAndamento = new DevFixture.Processo("aberto", ctx.ej.meses(), 3, 31,
                    (short) 2, null, 0, 0, "OPEN");
            publicar(ctx, "aberto", tituloDe(ctx.ej, ctx.ej.meses()), "OPEN", abre,
                    fimDoDia(agora.plus(Duration.ofDays(12))), emAndamento, agora, false);
        }
        return aprovados;
    }

    private List<Aprovado> publicar(Contexto ctx, String chave, String titulo, String status,
                                    Instant abre, Instant fecha, DevFixture.Processo processo,
                                    Instant ultimaChegada, boolean prazoEncerrado) {
        UUID processoId = id("processo:" + ctx.ej.chave() + ":" + chave);
        jdbc.update("""
                insert into selection_processes (id, tenant_id, title, description, status,
                    opens_at, closes_at, result_at, min_term, max_term, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, processoId, ctx.tenantId, titulo,
                "Processo seletivo da %s. Inscrições abertas a todos os cursos do catálogo."
                        .formatted(ctx.ej.nome()),
                status, ts(abre), ts(fecha),
                "CLOSED".equals(status) ? ts(fecha.plus(Duration.ofDays(21))) : null,
                processo.minTerm(), processo.maxTerm(), ts(abre.minus(Duration.ofDays(7))), ts(fecha));

        return candidaturas(ctx, processoId, chave, abre, ultimaChegada, processo, prazoEncerrado);
    }

    private List<Aprovado> candidaturas(Contexto ctx, UUID processoId, String chave, Instant abre,
                                        Instant ultimaChegada, DevFixture.Processo processo,
                                        boolean prazoEncerrado) {
        List<Object[]> linhas = new ArrayList<>();
        List<Object[]> links = new ArrayList<>();
        List<Object[]> arquivos = new ArrayList<>();
        List<Aprovado> aprovados = new ArrayList<>();
        long prazoEmSegundos = Duration.between(abre, ultimaChegada).getSeconds();
        int mesDeAdmissao = processo.mesAbertura() + processo.semanasDePrazo() / 4 + 2;

        for (int i = 0; i < processo.inscricoes(); i++) {
            UUID inscricaoId = id("inscricao:" + ctx.ej.chave() + ":" + chave + ":" + i);
            String nome = nomeDeCandidato(ctx, chave, i);
            String email = emailDe(nome, i);
            int curso = cursoDeCandidato(ctx, chave);
            Instant enviada = abre.plusSeconds(instanteDeEnvio(prazoEmSegundos, prazoEncerrado));

            UUID curriculo = null;
            if (filesEnabled && random.nextInt(100) < 68) {
                curriculo = id("curriculo:" + ctx.ej.chave() + ":" + chave + ":" + i);
                arquivos.add(new Object[] {curriculo, ctx.tenantId, "curriculo-" + (i + 1) + ".pdf",
                        "application/pdf", 120_000L + random.nextInt(400_000),
                        "puccomp-private-dev", ctx.tenantId + "/files/" + curriculo + ".pdf",
                        "READY", ts(enviada)});
            }

            linhas.add(new Object[] {inscricaoId, ctx.tenantId, processoId, nome, email, telefone(),
                    ctx.cursos.get(nomeDoCurso(ctx, curso)), periodo(processo), curriculo,
                    ts(enviada), ts(enviada), ts(enviada)});

            if (random.nextInt(100) < 34)
                links.add(new Object[] {inscricaoId, 0,
                        "https://github.com/" + email.substring(0, email.indexOf('@'))});

            // Os primeiros da lista são os aprovados. Os últimos deles ainda não aceitaram o
            // convite: é por isso que aprovado e membro não são a mesma quantidade.
            if (i < processo.aprovados())
                aprovados.add(new Aprovado(nome, email, curso, mesDeAdmissao,
                        i >= processo.aprovados() - processo.convites()));
        }

        if (!arquivos.isEmpty())
            jdbc.batchUpdate("""
                    insert into stored_files (id, tenant_id, filename, content_type, size, bucket,
                        object_key, state, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, arquivos);
        jdbc.batchUpdate("""
                insert into candidate_applications (id, tenant_id, process_id, full_name, email, phone,
                    course_id, current_term, cv_file_id, privacy_consent_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, linhas);
        if (!links.isEmpty())
            jdbc.batchUpdate("""
                    insert into candidate_application_links (application_id, link_order, url)
                    values (?, ?, ?)
                    """, links);
        return aprovados;
    }

    /**
     * Quase metade das inscrições cai no último dia do prazo. É o comportamento real de um processo
     * seletivo, e sem ele o pico do prazo — o insight do resumo — não apareceria. Processo ainda
     * aberto não recebe esse pico: ele não aconteceu, e antecipá-lo faria o resumo de um processo
     * em andamento parecer o de um encerrado.
     */
    private long instanteDeEnvio(long prazoEmSegundos, boolean prazoEncerrado) {
        double posicao = prazoEncerrado && random.nextInt(100) < 45
                ? 0.94 + random.nextDouble() * 0.06
                : Math.pow(random.nextDouble(), 0.7) * 0.94;
        return (long) (posicao * prazoEmSegundos);
    }

    /** O processo mais antigo ainda recebeu inscrições no curso que a EJ desativou depois. */
    private int cursoDeCandidato(Contexto ctx, String chave) {
        int limite = "ps1".equals(chave) ? ctx.ej.cursos().size() : ctx.ej.cursos().size() - 1;
        return random.nextInt(100) < 45 ? 0 : random.nextInt(Math.max(1, limite));
    }

    /** Um em cada nove não informa o período: é a categoria de id nulo da distribuição. */
    private Short periodo(DevFixture.Processo processo) {
        if (random.nextInt(9) == 0) return null;
        short minimo = processo.minTerm() == null ? 1 : processo.minTerm();
        short maximo = processo.maxTerm() == null ? 10 : processo.maxTerm();
        return (short) (minimo + random.nextInt(maximo - minimo + 1));
    }

    private String nomeDeCandidato(Contexto ctx, String chave, int indice) {
        int semente = Math.abs((ctx.ej.chave() + chave).hashCode());
        return PRIMEIROS_NOMES[(semente + indice * 7) % PRIMEIROS_NOMES.length] + " "
                + SOBRENOMES[(semente + indice * 13) % SOBRENOMES.length] + " "
                + SOBRENOMES[(semente + indice * 29 + 3) % SOBRENOMES.length];
    }

    private static String emailDe(String nome, int indice) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace(' ', '.');
        return base + "." + indice + "@aluno.puc.dev";
    }

    private String telefone() {
        return "31 9%04d-%04d".formatted(random.nextInt(10_000), random.nextInt(10_000));
    }

    private static String tituloDe(DevFixture.Ej ej, int mesAbertura) {
        LocalDate quando = LocalDate.ofInstant(mes(ej, mesAbertura), EJ);
        return "Processo Seletivo %d.%d".formatted(quando.getYear(),
                quando.getMonthValue() <= 6 ? 1 : 2);
    }

    /**
     * O prazo fecha no fim do dia: senão o último dia da curva seria uma manhã, e o pico do prazo —
     * o que o resumo existe para mostrar — se espalharia pelo dia anterior.
     */
    private static Instant fimDoDia(Instant instante) {
        return LocalDate.ofInstant(instante, EJ).atTime(23, 59).atZone(EJ).toInstant();
    }

    // ------------------------------------------------------------------ caixa

    /**
     * Caixa mês a mês: mensalidade de projetos como receita recorrente, projetos fechados como
     * receita eventual, e as despesas fixas da EJ. A divulgação de cada processo seletivo cai no
     * mês em que ele abriu — é o mesmo evento visto pelo financeiro.
     */
    private void caixa(Contexto ctx) {
        List<Object[]> lancamentos = new ArrayList<>();
        List<Integer> mesesDeProcesso = ctx.ej.processos().stream()
                .map(process -> process.mesAbertura()).toList();

        for (int mes = 0; mes <= ctx.ej.meses(); mes++) {
            LocalDate quando = LocalDate.ofInstant(mes(ctx.ej, mes), EJ);
            int escala = ctx.ej.lancamentosPorMes();

            lancamento(ctx, lancamentos, mes, "mensalidade", "INCOME", "Projetos",
                    "Mensalidade de projetos em andamento", 400 * escala + random.nextInt(320 * escala),
                    quando.withDayOfMonth(5));
            if (random.nextInt(100) < 62)
                lancamento(ctx, lancamentos, mes, "entrega", "INCOME", "Projetos",
                        "Entrega de projeto contratado", 560 * escala + random.nextInt(1_100 * escala),
                        quando.withDayOfMonth(18));
            if (random.nextInt(100) < 30)
                lancamento(ctx, lancamentos, mes, "consultoria", "INCOME", "Consultoria",
                        "Consultoria pontual para laboratório parceiro", 300 * escala + random.nextInt(500 * escala),
                        quando.withDayOfMonth(24));
            if (mes % 6 == 3)
                lancamento(ctx, lancamentos, mes, "evento", "INCOME", "Eventos",
                        "Inscrições em workshop aberto", 110 * escala + random.nextInt(220 * escala),
                        quando.withDayOfMonth(22));

            lancamento(ctx, lancamentos, mes, "ferramentas", "EXPENSE", "Ferramentas",
                    "Assinaturas de software e hospedagem", 60 * escala + random.nextInt(40 * escala),
                    quando.withDayOfMonth(3));
            lancamento(ctx, lancamentos, mes, "marketing", "EXPENSE", "Marketing",
                    "Impulsionamento e materiais gráficos", 32 * escala + random.nextInt(68 * escala),
                    quando.withDayOfMonth(12));
            lancamento(ctx, lancamentos, mes, "infra", "EXPENSE", "Infraestrutura",
                    "Rateio de sala e utilidades", 45 * escala + random.nextInt(15 * escala),
                    quando.withDayOfMonth(8));
            if (mes % 3 == 1)
                lancamento(ctx, lancamentos, mes, "treinamento", "EXPENSE", "Capacitação",
                        "Treinamento e certificações da equipe", 88 * escala + random.nextInt(175 * escala),
                        quando.withDayOfMonth(20));
            if (mes % 4 == 2)
                lancamento(ctx, lancamentos, mes, "federacao", "EXPENSE", "Federação",
                        "Contribuição à federação de empresas juniores", 140 * escala,
                        quando.withDayOfMonth(28));
            // O mesmo processo seletivo, visto pelo caixa.
            if (mesesDeProcesso.contains(mes))
                lancamento(ctx, lancamentos, mes, "divulgacao", "EXPENSE", "Recrutamento",
                        "Divulgação do processo seletivo", 75 * escala + random.nextInt(90 * escala),
                        quando.withDayOfMonth(10));
        }
        jdbc.batchUpdate("""
                insert into financial_entries (id, tenant_id, type, category, description, amount,
                    occurred_on, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, lancamentos);
    }

    /** Nada lançado no futuro: o mês corrente entra só até hoje, como um caixa de verdade. */
    private void lancamento(Contexto ctx, List<Object[]> destino, int mes, String chave, String tipo,
                            String categoria, String descricao, int valor, LocalDate quando) {
        if (quando.isAfter(LocalDate.now(EJ))) return;
        Instant carimbo = quando.atTime(10, 0).atZone(EJ).toInstant();
        destino.add(new Object[] {id("lancamento:" + ctx.ej.chave() + ":" + mes + ":" + chave),
                ctx.tenantId, tipo, categoria, descricao,
                new BigDecimal(valor + "." + (10 + random.nextInt(89))),
                java.sql.Date.valueOf(quando), ts(carimbo), ts(carimbo)});
    }

    private static final String[] PRIMEIROS_NOMES = {
            "Ana", "João", "Maria", "Pedro", "Luiza", "Gabriel", "Sofia", "Lucas", "Júlia", "Enzo",
            "Beatriz", "Felipe", "Clara", "Rafael", "Alice", "Daniel", "Manuela", "Bruno", "Laura",
            "Caio", "Heloísa", "Iago", "Nina", "Otávio", "Yasmin", "Vitor", "Elisa", "Murilo",
            "Antônia", "Kauã", "Lorena", "Théo", "Cecília", "Davi", "Isis", "Noah", "Bianca"};

    private static final String[] SOBRENOMES = {
            "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Almeida", "Costa",
            "Gomes", "Martins", "Araújo", "Melo", "Barbosa", "Ribeiro", "Carvalho", "Teixeira",
            "Moreira", "Cardoso", "Nascimento", "Lopes", "Pereira", "Correia", "Azevedo", "Fonseca",
            "Bittencourt", "Quintela", "Rangel", "Vasques", "Peixoto", "Sampaio", "Bastos"};
}
