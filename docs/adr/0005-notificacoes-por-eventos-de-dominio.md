# 0005 — Notificações por eventos de domínio, com outbox

- **Status:** proposto
- **Data:** 2026-09-15
- **Decisores:** squad PUC COMP

## Contexto e problema

Todo aviso por e-mail saía de dentro do serviço que produzia o fato. O
`CandidateApplicationService` carregava assunto, nome de template, mapa de
variáveis, fuso horário e um helper de primeiro nome — no meio da regra de
negócio de inscrição. `identity` fazia o mesmo para convite e senha.

Isso custava em três frentes, e as três apareceram ao mesmo tempo:

1. **Alcance.** O candidato recebia exatamente um e-mail na vida — a confirmação
   — e nunca mais ouvia falar do processo, mesmo com uma data de resultado
   publicada no formulário que ele preencheu. Aposentar um membro revogava acesso
   em silêncio. Um convite aceito não avisava quem convidou. Cada um desses avisos
   exigiria mexer no módulo de negócio correspondente.
2. **Confiabilidade.** `AsyncMailDeliverer` capturava qualquer falha e chamava
   `log.error`. Uma indisponibilidade de SMTP fazia a confirmação prometida ao
   candidato desaparecer, sem registro e sem forma de reenviar.
3. **Direção da dependência.** `recruitment` dependia de `notification` e de
   `email` para poder avisar. Todo módulo que passasse a notificar herdaria as
   duas dependências.

## Opções consideradas

- **Manter a chamada direta e só acrescentar avisos** — cada serviço de negócio
  ganha mais um `mailer.send(...)` por fato novo.
- **Chamada direta com retry no envio** — mantém o desenho e resolve só a
  confiabilidade, com backoff dentro do pool de e-mail.
- **Eventos de domínio com o registro de publicações do Spring Modulith** — o
  módulo publica o fato; `notification` escuta. A publicação é gravada na mesma
  transação do fato (outbox) e só é marcada como completa quando o listener
  termina.
- **Fila externa (SQS, RabbitMQ)** — mesma inversão, com durabilidade fora do
  banco.

## Decisão

Escolhemos os **eventos de domínio com o registro de publicações do Modulith**.

O critério que desempatou foi o custo de operação contra o que se ganha. A fila
externa entrega o mesmo desacoplamento e uma durabilidade melhor, mas acrescenta
um serviço para operar, monitorar e pagar — para uma plataforma que ainda roda em
uma instância e um Postgres. O registro do Modulith usa a transação que já existe:
a inscrição e o aviso pendente são gravados juntos, ou nenhum dos dois.

A inversão da dependência veio junto e é metade do valor: `recruitment`,
`organization` e `identity` publicam fatos e não conhecem mais `notification` nem
`email`. Um aviso novo se escreve inteiro dentro de `notification`.

O evento carrega o `tenantId` explicitamente. O listener é assíncrono e a
republicação no reinício não tem requisição de onde herdar o tenant — e o
Hibernate resolve o tenant **quando a sessão abre**, não quando a consulta roda.
Por isso o escopo é aberto antes da transação do listener (`TenantScope`), e não
dentro dela.

## Consequências

- 🟢 **Boa:** um aviso novo não toca no módulo que produziu o fato. Os cinco
  avisos acrescentados nesta mudança — fase do processo para candidatos, abertura
  para a equipe, vínculo alterado, atribuição alterada, convite aceito — são
  código só em `notification`.
- 🟢 **Boa:** aviso com destinatário único é entregue de forma durável
  (`Mailer.deliver`): a falha mantém a publicação incompleta e o aviso volta a ser
  tentado, inclusive depois de um reinício.
- 🟢 **Boa:** os módulos de negócio deixam de importar `email`, e o grafo do
  Modulith fica com uma seta a menos em cada um.
- 🔴 **Ruim:** aviso com fan-out (equipe com uma permissão, candidatos de um
  processo) continua em melhor esforço, com `Mailer.send`. Reprocessar uma falha
  no meio de centenas de envios reenviaria para quem já recebeu, e a idempotência
  por destinatário exigiria registrar cada disparo — custo que não se justifica
  ainda.
- 🔴 **Ruim:** o teste de um aviso passa a atravessar um salto assíncrono, e as
  verificações precisam esperar a entrega em vez de exigi-la pronta.
- ⚪ **Neutra:** a tabela `event_publication` (migration `V20`) passa a crescer com
  as publicações completas. O modo de conclusão é `update`, que preserva o
  histórico; trocar para `delete` ou `archive` é configuração, não código.
- ⚪ **Neutra:** o aviso por inscrição virou configurável
  (`puccomp.notification.new-application`). No modo `digest`, um resumo diário
  substitui um e-mail por inscrição por destinatário — que num dia de prazo, com
  200 inscrições e 5 pessoas com a permissão, seriam mil mensagens.
