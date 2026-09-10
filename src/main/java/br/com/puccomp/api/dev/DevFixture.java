package br.com.puccomp.api.dev;

import java.util.List;

/**
 * O elenco das EJs de demonstração, escrito à mão para que cada caso do contrato tenha alguém que o
 * exerça: cargo cheio, cargo acima da capacidade, cargo sem capacidade declarada, cargo vago, cargo
 * desativado, diretoria vazia, membro sem cargo, alumni, afastado e quem saiu e voltou.
 *
 * <p>Os meses são deslocamentos a partir do início do rastreamento, não datas fixas: assim a massa
 * continua contando a mesma história em qualquer dia em que a API subir.
 *
 * <p>São duas EJs de tamanhos bem diferentes, e não uma só, porque quase todo defeito de isolamento
 * é invisível quando existe um tenant apenas — a segunda EJ é o que faz "vazou" parecer diferente
 * de "está certo".
 */
final class DevFixture {

    private DevFixture() { }

    record Curso(String nome, boolean ativo) { }

    record Diretoria(String chave, String nome, String descricao, boolean ativa) { }

    /** {@code vagas} nulo é capacidade desconhecida; zero é capacidade declarada como nenhuma. */
    record Cargo(String chave, String nome, String descricao, String diretoria, Integer vagas,
                 boolean ativo, List<String> permissoes) { }

    /**
     * @param entrada    mês da admissão
     * @param saida      mês em que deixou de ser ativo, ou nulo
     * @param statusApos estado após a saída
     * @param retorno    mês em que voltou a ser ativo, ou nulo
     */
    record Pessoa(String nome, int curso, String cargo, String diretoria, int entrada,
                  Integer saida, String statusApos, Integer retorno) {

        static Pessoa ativa(String nome, int curso, String cargo, int entrada) {
            return new Pessoa(nome, curso, cargo, null, entrada, null, null, null);
        }

        static Pessoa semCargo(String nome, int curso, String diretoria, int entrada) {
            return new Pessoa(nome, curso, null, diretoria, entrada, null, null, null);
        }

        static Pessoa saiu(String nome, int curso, String cargo, int entrada, int saida, String status) {
            return new Pessoa(nome, curso, cargo, null, entrada, saida, status, null);
        }

        static Pessoa voltou(String nome, int curso, String cargo, int entrada, int saida,
                             String status, int retorno) {
            return new Pessoa(nome, curso, cargo, null, entrada, saida, status, retorno);
        }

        String statusFinal() {
            return saida == null || retorno != null ? "ACTIVE" : statusApos;
        }
    }

    /**
     * Um processo seletivo e a turma que saiu dele.
     *
     * <p>É aqui que os módulos se encontram: os aprovados de {@code recruitment} viram os membros de
     * {@code organization}, com o mesmo nome e o mesmo curso da inscrição. Sem isso a massa teria
     * duas populações desconexas, e nenhum painel que ligue captação a quadro faria sentido.
     *
     * @param aprovados quantos dos inscritos viram membro no mês seguinte ao encerramento
     * @param convites  quantos dos aprovados ainda não aceitaram o convite
     */
    record Processo(String chave, int mesAbertura, int semanasDePrazo, int inscricoes,
                    Short minTerm, Short maxTerm, int aprovados, int convites, String status) { }

    /** Uma EJ inteira: catálogo, quadro escrito à mão, processos seletivos e escala do caixa. */
    record Ej(String chave, String nome, String slug, String dominio, int meses,
              List<Curso> cursos, List<Diretoria> diretorias, List<Cargo> cargos,
              List<Pessoa> nucleo, List<Processo> processos, List<String> cargosDeTurma,
              String cargoDeEntrada, boolean processoAberto, int lancamentosPorMes) { }

    // ------------------------------------------------------------------ EJ Comp

    private static final List<Curso> CURSOS_COMP = List.of(
            new Curso("Ciência da Computação", true),
            new Curso("Ciência de Dados", true),
            new Curso("Engenharia de Software", true),
            new Curso("Engenharia de Computação", true),
            new Curso("Sistemas de Informação", true),
            // Desativado: some do formulário sem invalidar quem já se inscreveu com ele.
            new Curso("Análise e Desenvolvimento de Sistemas", false));

    private static final List<Diretoria> DIRETORIAS_COMP = List.of(
            new Diretoria("PRESIDENCIA", "Presidência", "Direção geral da EJ", true),
            new Diretoria("COMERCIAL", "Comercial", "Prospecção e relacionamento com clientes", true),
            new Diretoria("PROJETOS", "Projetos", "Execução e entrega dos projetos contratados", true),
            new Diretoria("GENTE", "Gente e Gestão", "Recrutamento, cultura e desenvolvimento", true),
            new Diretoria("MARKETING", "Marketing", "Marca, conteúdo e presença digital", true),
            new Diretoria("FINANCEIRO", "Financeiro", "Orçamento, caixa e prestação de contas", true),
            // Ativa e sem nenhum membro ativo: é a lacuna que empty_departments existe para mostrar.
            new Diretoria("INOVACAO", "Inovação", "Frente nova, ainda sem equipe alocada", true),
            // Desativada: não aparece em lacuna nenhuma, e seus cargos saem da capacidade.
            new Diretoria("QUALIDADE", "Qualidade", "Descontinuada na reestruturação", false));

    private static final List<Cargo> CARGOS_COMP = List.of(
            new Cargo("PRESIDENTE", "Presidente", "Representa a EJ e conduz a diretoria executiva",
                    "PRESIDENCIA", 1, true, List.of()),
            new Cargo("VICE", "Vice-Presidente", "Apoia a presidência e cuida da estratégia",
                    "PRESIDENCIA", 1, true,
                    List.of("members:read", "roles:read", "departments:read", "financial:read")),
            new Cargo("DIR_COMERCIAL", "Diretor Comercial", "Responde pela carteira de clientes",
                    "COMERCIAL", 1, true, List.of("members:read", "recruitment:read")),
            new Cargo("ANALISTA_COMERCIAL", "Analista Comercial", "Prospecta e acompanha propostas",
                    "COMERCIAL", 8, true, List.of("members:read")),
            new Cargo("DIR_PROJETOS", "Diretor de Projetos", "Responde pela entrega dos projetos",
                    "PROJETOS", 1, true,
                    List.of("members:read", "roles:read", "departments:read", "recruitment:read")),
            new Cargo("GERENTE_PROJETOS", "Gerente de Projetos", "Conduz um portfólio de projetos",
                    "PROJETOS", 4, true, List.of("members:read", "recruitment:read")),
            new Cargo("DEV", "Desenvolvedor", "Executa os projetos técnicos da EJ",
                    "PROJETOS", 20, true, List.of("members:read")),
            new Cargo("DIR_GENTE", "Diretor de Gente e Gestão", "Responde por recrutamento e cultura",
                    "GENTE", 1, true, List.of("members:read", "members:write", "members:invite",
                    "roles:read", "departments:read", "recruitment:read", "recruitment:write")),
            new Cargo("ANALISTA_RH", "Analista de Gente e Gestão", "Conduz processos seletivos e cultura",
                    "GENTE", 6, true, List.of("members:read", "recruitment:read", "recruitment:write")),
            new Cargo("DIR_MARKETING", "Diretor de Marketing", "Responde pela marca e pelo funil",
                    "MARKETING", 2, true, List.of("members:read", "recruitment:read")),
            // Capacidade menor do que a equipe que se formou: open negativo, que é excesso de
            // ocupação e não erro de conta.
            new Cargo("DESIGNER", "Designer", "Identidade visual e materiais da EJ",
                    "MARKETING", 4, true, List.of("members:read")),
            new Cargo("DIR_FINANCEIRO", "Diretor Financeiro", "Responde pelo caixa e pelo orçamento",
                    "FINANCEIRO", 1, true,
                    List.of("members:read", "financial:read", "financial:write")),
            new Cargo("ANALISTA_FINANCEIRO", "Analista Financeiro", "Lançamentos e prestação de contas",
                    "FINANCEIRO", 4, true, List.of("financial:read", "financial:write")),
            // Capacidade desconhecida: não é zero nem ilimitada, e seus ocupantes ficam fora do saldo.
            new Cargo("TRAINEE", "Trainee", "Em formação, ainda sem alocação definitiva",
                    null, null, true, List.of()),
            // Vago e com capacidade: é o que unfilled_roles deve apontar.
            new Cargo("ASSESSOR_INOVACAO", "Assessor de Inovação", "Frente nova, a preencher",
                    "INOVACAO", 3, true, List.of()),
            // Capacidade declarada como zero: vago, mas não é vaga a preencher.
            new Cargo("CONSULTOR_SENIOR", "Consultor Sênior", "Posição congelada nesta gestão",
                    null, 0, true, List.of()),
            // Desativado: não oferece capacidade, e quem o ocupa continua visível na composição.
            new Cargo("COORD_QUALIDADE", "Coordenador de Qualidade", "Extinto na reestruturação",
                    "QUALIDADE", 1, false, List.of()));

    private static final List<Pessoa> NUCLEO_COMP = List.of(
            Pessoa.ativa("Ana Beatriz Ramos", 0, "PRESIDENTE", 0),
            Pessoa.ativa("Carlos Eduardo Lima", 2, "VICE", 0),
            Pessoa.ativa("Mariana Alves Costa", 0, "DIR_PROJETOS", 0),
            Pessoa.ativa("Rafael Souza Pinto", 1, "DIR_COMERCIAL", 0),
            Pessoa.ativa("Juliana Ferreira Braz", 4, "DIR_GENTE", 0),
            Pessoa.ativa("Bruno Henrique Dias", 2, "DIR_FINANCEIRO", 1),
            Pessoa.ativa("Larissa Monteiro Sá", 3, "DIR_MARKETING", 1),
            Pessoa.ativa("Pedro Henrique Barros", 2, "GERENTE_PROJETOS", 2),
            Pessoa.saiu("Thiago Nogueira Alencar", 0, "DEV", 0, 22, "ALUMNUS"),
            Pessoa.saiu("Camila Rocha Vieira", 1, "DEV", 0, 14, "ALUMNUS"),
            // Saiu e voltou: a saída de lá continua contada, e o afastamento é reconstituído.
            Pessoa.voltou("Lucas Andrade Pimenta", 1, "ANALISTA_RH", 6, 18, "INACTIVE", 24),
            Pessoa.ativa("Isabela Martins Leal", 0, "DEV", 6),
            Pessoa.ativa("Beatriz Nunes Carvalho", 4, "DESIGNER", 6),
            Pessoa.ativa("Vinícius Prado Camargo", 1, "GERENTE_PROJETOS", 12),
            Pessoa.ativa("Sofia Barreto Queiroz", 4, "DESIGNER", 18),
            Pessoa.saiu("Davi Lucca Freitas", 0, "TRAINEE", 18, 23, "INACTIVE"),
            Pessoa.ativa("Valentina Peixoto Cruz", 4, "DESIGNER", 22),
            // Único ocupante de um cargo desativado: some da capacidade, fica na composição.
            Pessoa.ativa("Renata Aguiar Pontes", 1, "COORD_QUALIDADE", 22),
            Pessoa.ativa("Heitor Salgado Vieira", 0, "TRAINEE", 25),
            Pessoa.semCargo("Lorena Sampaio Xavier", 0, "PROJETOS", 28),
            Pessoa.semCargo("Anthony Cardim Rebelo", 2, "COMERCIAL", 28),
            Pessoa.semCargo("Melissa Tanaka Okada", 1, null, 28));

    static final Ej COMP = new Ej("comp", "EJ Comp", "ej-comp", "ejcomp.dev", 30,
            CURSOS_COMP, DIRETORIAS_COMP, CARGOS_COMP, NUCLEO_COMP,
            List.of(
                    new Processo("ps1", 4, 3, 46, (short) 1, (short) 8, 8, 0, "CLOSED"),
                    new Processo("ps2", 10, 3, 58, (short) 1, (short) 10, 10, 0, "CLOSED"),
                    new Processo("ps3", 16, 4, 63, (short) 2, (short) 10, 9, 0, "CLOSED"),
                    // O mais recente ainda tem dois aprovados que não aceitaram o convite.
                    new Processo("ps4", 23, 3, 55, (short) 1, (short) 10, 9, 2, "IN_REVIEW")),
            // Ordem em que os aprovados são alocados; repete até acabar a turma.
            List.of("DEV", "DEV", "ANALISTA_COMERCIAL", "DEV", "ANALISTA_RH", "DEV", "DESIGNER",
                    "DEV", "ANALISTA_FINANCEIRO", "TRAINEE", "DEV", "GERENTE_PROJETOS"),
            "TRAINEE", true, 8);

    // ------------------------------------------------------------------ EJ Dados

    static final Ej DADOS = new Ej("dados", "EJ Dados", "ej-dados", "ejdados.dev", 18,
            List.of(new Curso("Ciência de Dados", true),
                    new Curso("Estatística", true),
                    new Curso("Ciência da Computação", true)),
            List.of(new Diretoria("PRESIDENCIA", "Presidência", "Direção geral da EJ", true),
                    new Diretoria("ANALYTICS", "Analytics", "Modelagem, dados e entregas analíticas", true),
                    new Diretoria("PARCERIAS", "Parcerias", "Convênios com laboratórios e empresas", true)),
            List.of(new Cargo("PRESIDENTE", "Presidente", "Representa a EJ", "PRESIDENCIA", 1, true,
                            List.of()),
                    new Cargo("DIR_ANALYTICS", "Diretor de Analytics", "Responde pelas entregas analíticas",
                            "ANALYTICS", 1, true,
                            List.of("members:read", "roles:read", "departments:read", "recruitment:read")),
                    new Cargo("CIENTISTA", "Cientista de Dados", "Modelagem e experimentação",
                            "ANALYTICS", 8, true, List.of("members:read")),
                    new Cargo("ANALISTA_DADOS", "Analista de Dados", "Preparo de dados e relatórios",
                            "ANALYTICS", 6, true, List.of("members:read")),
                    new Cargo("PARCERIAS", "Assessor de Parcerias", "Convênios e captação",
                            "PARCERIAS", 3, true, List.of("members:read", "recruitment:read"))),
            List.of(Pessoa.ativa("Helena Castro Vidal", 0, "PRESIDENTE", 0),
                    Pessoa.ativa("Otávio Menezes Lira", 1, "DIR_ANALYTICS", 0),
                    Pessoa.ativa("Marina Bezerra Coelho", 0, "CIENTISTA", 1),
                    Pessoa.saiu("Rodrigo Paiva Sena", 2, "CIENTISTA", 1, 11, "ALUMNUS"),
                    Pessoa.ativa("Clarice Do Vale Pinho", 1, "ANALISTA_DADOS", 3),
                    Pessoa.semCargo("Igor Vasques Rondon", 2, null, 15)),
            List.of(new Processo("ps1", 6, 2, 34, (short) 2, (short) 10, 6, 1, "CLOSED")),
            List.of("CIENTISTA", "ANALISTA_DADOS", "PARCERIAS", "CIENTISTA"),
            "ANALISTA_DADOS", false, 4);

    static final List<Ej> EJS = List.of(COMP, DADOS);
}
