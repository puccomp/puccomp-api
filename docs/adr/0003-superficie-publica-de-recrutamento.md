# 0003 — Superfície pública de recrutamento

- **Status:** aceito
- **Data:** 2026-08-10
- **Decisores:** squad PUC COMP

## Contexto e problema

O módulo de recrutamento é o primeiro do sistema com **escrita anônima**: um
candidato que não tem conta precisa ver um processo seletivo e se inscrever nele.

Isso quebra a premissa que sustentava todo o isolamento multi-tenant até aqui —
o tenant vinha do token de autenticação, fixado pelo `BearerAuthenticationFilter`.
Numa requisição anônima não há token, e o Hibernate precisa de um tenant para
aplicar o filtro de `@TenantId`. Alguém tem que dizer de qual EJ é aquela
requisição, e a escolha de **quem** e **onde** define a segurança do isolamento.

Em paralelo, havia a questão de **quais campos** o formulário coleta: cada EJ tem
sua ficha de inscrição, com perguntas próprias.

## Opções consideradas

- **Tenant derivado do ID do recurso** — o candidato faz `POST` no ID do processo,
  e o servidor descobre a EJ a partir dele. Exige uma consulta que ignora o filtro
  de tenant e um `TenantContext.set()` dentro do service.
- **Token opaco por processo (capability URL)** — a EJ gera um link secreto
  (`?token=apl_...`) e o token identifica a EJ. Mesmo desenho de `Invitation`.
- **Slug da EJ na URL** — `/v1/public/{ejSlug}/...`, com o tenant resolvido num
  filtro antes de a requisição chegar ao domínio.
- **Subdomínio por EJ** — `ejcomp.puccomp.app`, tenant vindo do `Host`.

Para os campos do formulário, consideramos deixar cada EJ fazer o **CRUD das
próprias perguntas** (campos customizados, tipados, com ordem e obrigatoriedade),
guardando as respostas como JSON na inscrição.

## Decisão

Escolhemos o **slug da EJ na URL**, com a resolução do tenant num filtro
(`PublicTenantFilter`, em `identity/security`), e **campos de formulário fixos**.

O que desempatou contra o ID do recurso foi que ele obriga o domínio a furar o
próprio isolamento: uma consulta sem filtro de tenant e um `TenantContext.set()`
dentro de um service de negócio. É um padrão que, uma vez no repositório, vira
exemplo copiado — e o custo de errar aqui é vazamento entre EJs.

O que desempatou contra o token opaco foi o **caso de uso real**. Capability URL
pressupõe "possuir o link = ter permissão", o que é perfeito para um convite
individual. Mas um formulário de processo seletivo **existe para ser transmitido**:
story no Instagram, grupo de WhatsApp, QR code em cartaz. Se todo mundo tem o
link, ele não autoriza nada — vira um identificador obscuro que ainda por cima
não é indexável pelo Google, vaza pelo header `Referer` e cuja revogação quebraria
todo cartaz já impresso. O que precisamos ali não é sigilo, é **autorização**:
_só processo `OPEN` de EJ `ACTIVE`, e só a projeção pública_.

O subdomínio é provavelmente o destino final (é o padrão de SaaS B2B e o mais
brandável), mas exige DNS wildcard e TLS — infra que não temos hoje. O slug é o
mesmo modelo mental e migra para subdomínio sem mudar o domínio: só troca quem
alimenta o filtro.

Sobre os campos: o CRUD de perguntas por EJ é a resposta certa **a prazo**, e a
demanda é real. Ficamos no simples agora porque ele arrasta consigo um construtor
de formulários, validação dinâmica, versionamento das perguntas (uma inscrição
antiga precisa continuar legível depois que a EJ edita a ficha) e uma tela de
administração — muito para um bootstrap. O conjunto fixo atual cobre o essencial
e não fecha nenhuma porta: campos customizados entram como uma tabela de
definições mais um JSON de respostas, sem tocar no que já existe.

## Consequências

- 🟢 **Boa:** nenhum service de negócio resolve tenant. Sob `/v1/public/**` ele
  vem do slug, no resto vem do token — sempre num filtro, nunca no domínio.
- 🟢 **Boa:** ser público fica visível na URL. O `SecurityConfig` libera
  `/v1/public/**` numa linha, em vez de enumerar rota por rota — uma rota pública
  nova não depende de alguém lembrar de liberá-la.
- 🟢 **Boa:** links compartilháveis, indexáveis e estáveis. A EJ divulga uma URL
  legível e ela não expira.
- 🟢 **Boa:** caminho aberto para subdomínio por EJ sem mexer no domínio.
- 🔴 **Ruim:** processos abertos são enumeráveis por slug. Aceitamos: são
  públicos por definição, e a projeção pública não expõe status, auditoria nem
  tenant. Um processo em `DRAFT` responde 404 — indistinguível de inexistente.
- 🔴 **Ruim:** a ficha de inscrição é igual para todas as EJs. Quem precisar de
  perguntas próprias terá que esperar o card de campos customizados.
- ⚪ **Neutra:** o token opaco continua sendo o desenho certo para o que é
  individual — convite direcionado ou o candidato acompanhar a própria inscrição.
  Se entrar, entra **por candidato**, não por processo.
- ⚪ **Neutra:** o `PublicTenantFilter` roda depois do `BearerAuthenticationFilter`
  e sobrescreve o tenant. Numa rota pública quem manda é o slug, mesmo que o
  chamador esteja autenticado por outra EJ.
