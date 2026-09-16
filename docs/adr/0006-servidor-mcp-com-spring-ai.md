# 0006 — Servidor MCP stateless, autenticado por PAT

- **Status:** proposto
- **Data:** 2026-09-15
- **Decisores:** squad PUC COMP

## Contexto e problema

O `CLAUDE.md` descreve a plataforma como AI-first e afirma que ela "disponibiliza
uma API RESTful e um servidor MCP". A segunda metade não existia: havia a promessa
e nenhum código.

A força que exige a decisão é concreta. O membro da EJ já usa um agente de IA no
dia a dia — Claude Code, Cursor, Claude Desktop — e hoje, para perguntar "quantos
candidatos estão na etapa de entrevista?", ele sai da ferramenta, abre o sistema e
procura. Encurtar esse caminho é o ponto. O que não pode acontecer, em nenhuma
hipótese, é o agente ver mais do que a pessoa que o conectou: mesma EJ, mesmas
permissões, mesmos limites.

Duas coisas tornaram isso barato agora. O MCP virou stateless na
[spec de 2026-07-28](https://blog.modelcontextprotocol.io/posts/2026-07-28/) — sem
handshake `initialize`, sem `Mcp-Session-Id` — e a nossa API já é stateless ponta a
ponta. E o submódulo PAT já resolve credencial, tenant e permissão por requisição:
para a aplicação, um agente com um PAT é indistinguível de um membro com um PAT.

## Opções consideradas

- **Não fazer nada e publicar o OpenAPI** — o agente consome a API REST pela spec,
  sem servidor MCP.
- **Servidor MCP reativo (`webflux`) e `ASYNC`** — o desenho que a documentação do
  Spring AI apresenta primeiro, com a promessa de não bloquear thread.
- **Servidor MCP `webmvc`, `STATELESS` e `SYNC`, autenticado por PAT** — o
  transporte entra na mesma cadeia de filtros do resto da API.
- **OAuth 2.1 com Dynamic Client Registration** — o fluxo que habilita conector de
  um clique no Claude.ai e no ChatGPT.

## Decisão

Escolhemos o **servidor `webmvc` + `STATELESS` + `SYNC`, autenticado por PAT**.

### Por que não o reativo — e por que isso é de segurança, não de desempenho

O critério que desempatou foi o `TenantContext`. Ele é um `ThreadLocal`, e o
`MultiTenancyConfig` cai num tenant zerado quando ele está vazio — **em silêncio,
sem exceção**. O `SecurityContextHolder`, de que o `@PreAuthorize` depende, também
é preso à thread.

Em `ASYNC`, ou sob `webflux`, o método da ferramenta roda fora da thread da
requisição. O `TenantContext` volta nulo, o Hibernate resolve o tenant zerado, e a
ferramenta devolve uma lista vazia — ou, com um filtro diferente, os dados errados.
Nenhum log, nenhum erro, nenhum sintoma. É o mesmo mecanismo que o ADR 0005
descreve no `TenantScope`: o Hibernate resolve o tenant **quando a sessão abre**,
não quando a consulta roda.

O argumento de desempenho a favor do reativo também não se sustenta aqui. Quem
espera numa conversa com agente é o cliente: ele encadeia tooling e aguarda o LLM.
Do nosso lado, uma chamada de ferramenta é desserializar JSON-RPC, consultar o
Postgres e serializar. E não-bloqueante de verdade exigiria trocar o JPA por R2DBC:
sem isso, o WebFlux entrega o mesmo bloqueio empurrado para um `boundedElastic`,
com a complexidade reativa e a migração obrigatória de `TenantContext` e
`SecurityContextHolder` para o `Context` do Reactor por cima. O gargalo real, nos
dois modelos, é o pool do Hikari.

A alavanca certa para a rajada de chamadas do agente são as virtual threads, ligadas
em commit próprio: a requisição parada em I/O desmonta da carrier thread, e o
`ThreadLocal` continua funcionando — ou seja, o `TenantContext` segue correto sem
alteração nenhuma.

### Por que PAT, e não OAuth

OAuth 2.1 com DCR é o que habilita conector de um clique, e é para onde isto vai.
Mas ele é um card inteiro — servidor de autorização, registro dinâmico de cliente,
consentimento — e não muda nada do que está decidido aqui: o transporte, o modelo
de thread e o lugar das ferramentas continuam os mesmos. O PAT entrega o valor
agora com o código que já existe, e o endurecimento que ele precisava (validação de
escopo, menos escrita por chamada) coube num commit.

### Uma classe de ferramentas por módulo, no pacote raiz dele

Cada módulo é dono das suas ferramentas, numa classe só: `RecruitmentTools`,
`OrganizationTools`, `FinancialTools`, `IdentityTools`. Quem procura o que o agente
enxerga de recrutamento abre um arquivo, e não dois espalhados por sub-pacotes.

Um módulo `mcp` central seria pior por dois motivos. O primeiro é que ele importaria
os serviços de todos os módulos, criando no grafo do Modulith exatamente as setas que
o `ModularityTests` existe para impedir. O segundo é que não sobraria nada para morar
nele além disso: o starter autoconfigura o transporte inteiro a partir de propriedades.

O preço de juntar as ferramentas de um módulo num arquivo é que os serviços que elas
chamam deixaram de ser package-private — `SelectionProcessService` e
`CandidateApplicationService` vivem em sub-pacotes diferentes do mesmo módulo, e Java
não tem visibilidade de módulo. Só a classe e os métodos de leitura usados abriram;
escrita continua fechada. E o que fecha o módulo para fora nunca foi o `package-private`:
é o Modulith, que só deixa outro módulo importar do pacote raiz ou de um
`@NamedInterface` — `recruitment.processes` não é nenhum dos dois, e o
`ModularityTests` reprova quem tentar, público ou não.

Acoplamento novo entre módulos não nasce disto: a classe de ferramenta só acrescenta
anotações do Spring AI, que é biblioteca e não módulo.

### snake_case nos dois sentidos, e o que isso custou

A superfície do MCP é `snake_case` como a da API REST. A alternativa seria deixar
cada caminho com a sua convenção, e ela é pior por um motivo prático: as descrições
das ferramentas foram escritas a partir da documentação REST e citam `active_headcount`,
`process_id`, `min_term`. Descrição que nomeia um campo que não chega é pior que
descrição nenhuma.

Custou duas acomodações, ambas por não haver ponto de configuração:

- **Na saída**, o Spring AI serializa o retorno com um `JsonHelper` estático que chama
  `JacksonUtils.getDefaultJsonMapper()` — ele ignora a configuração Jackson do Spring,
  e não há como injetar outro. Como um retorno do tipo `String` é embutido literalmente
  no conteúdo da resposta, cada ferramenta serializa com o `ObjectMapper` da aplicação
  e devolve `String`. Alinha nome, data e número de uma vez, e não pode divergir depois
  — ao contrário de anotar record por record, onde esquecer um passa em silêncio.
- **Na entrada**, o nome do parâmetro Java é ao mesmo tempo o nome publicado no schema
  e o nome pelo qual o argumento é vinculado. Não há como renomear um sem o outro, então
  as assinaturas das ferramentas usam sublinhado. Só elas.

### O que a segurança precisou

Quase nada, e esse é o ponto. A cadeia de ordem 2 termina em
`anyRequest().authenticated()` e `/mcp` não está em nenhum `permitAll`, então o
endpoint **nasce autenticado** — o oposto do padrão inseguro de que a própria
documentação do Spring AI avisa. O `BearerAuthenticationFilter` já roda antes e já
entende o prefixo `pat_`, e o endpoint do starter é uma `RouterFunction`, que passa
pelo `DispatcherServlet` e portanto pela cadeia de filtros.

Sobrou o CORS: a allowlist de cabeçalhos é explícita, e um cliente MCP em navegador
manda `MCP-Protocol-Version`.

## Consequências

- 🟢 **Boa:** o isolamento entre EJs não depende de disciplina de quem escreve
  ferramenta. Ele vem do filtro que já existia, e está preso por um teste que uma
  troca para `ASYNC` reprovaria.
- 🟢 **Boa:** uma ferramenta nova é uma anotação num método ao lado do controller.
  Escrita, quando chegar, é o mesmo método com outro `@PreAuthorize` — não uma
  refatoração.
- 🟢 **Boa:** erro de domínio chega ao agente legível de graça: o Spring AI captura
  a exceção e devolve `isError: true` com a mensagem e a causa raiz, sem stacktrace.
  Não escrevemos mapeamento nenhum.
- 🔴 **Ruim:** sem OAuth, conectar exige colar um PAT na configuração do cliente.
  Funciona em Claude Code e Cursor; não dá conector de um clique no Claude.ai.
- 🔴 **Ruim:** a recusa do `@PreAuthorize` chega ao agente como um "Access Denied"
  seco, que não diz o que faltou. Contornamos por dois lados: a descrição de cada
  ferramenta nomeia a permissão exigida, e `whoami` publica as permissões efetivas —
  já com o escopo do token aplicado — para o agente saber o que vai ser recusado
  antes de tentar.
- 🔴 **Ruim:** `SYNC` fecha a porta para uma ferramenta genuinamente demorada
  (relatório pesado, chamada a outro LLM, S3) ocupar a thread. O caminho, quando
  aparecer, é o que a própria spec de 2026-07-28 recomenda: devolver um *handle*
  como saída da ferramenta e deixar o agente buscar o resultado por outra — o que
  funciona em `SYNC`.
- ⚪ **Neutra:** o Spring AI 2.0.1 fixa `spring-boot-starter-web` 4.1.1 no POM
  publicado, o que obrigou a subir o Boot de 4.0.6 para 4.1.1 — e, junto, o Modulith
  e o springdoc, cada um construído contra um Boot específico.
- 🔴 **Ruim:** as ferramentas de recrutamento devolvem texto escrito por terceiros —
  nome, e-mail e links vêm de um formulário público, preenchido por quem não é membro
  da EJ. É uma superfície de injeção de prompt que já existia como dado e agora chega
  a um agente. As instruções do servidor mandam tratar isso como dado a relatar e não
  seguir os links, o que reduz mas não elimina; defesa de verdade exige marcação de
  conteúdo não confiável, que o protocolo ainda não oferece.
- 🔴 **Ruim:** não há limite de chamadas por token nem trilha do que o agente leu.
  Um agente em laço bate no Postgres na velocidade da rede, e hoje ninguém consegue
  reconstruir depois o que foi consultado. Vale um card assim que houver uso real.
- 🔴 **Ruim:** a superfície de recrutamento não responde "quantos candidatos estão na
  etapa de entrevista?", que foi a pergunta que motivou este servidor. A inscrição não
  tem etapa no modelo — a "fase" que o ADR 0005 notifica é o status do processo, não do
  candidato. É lacuna de produto, e nenhuma ferramenta a contorna.
- ⚪ **Neutra:** as ferramentas devolvem `ToolPage` em vez do `Page` do Spring Data.
  O envelope do Spring Data é contexto que o agente paga em toda chamada para
  decidir uma única coisa — se vale pedir a próxima página. Sendo o mesmo tipo em
  toda listagem, ele aprende a ler um envelope, e não um por módulo.
