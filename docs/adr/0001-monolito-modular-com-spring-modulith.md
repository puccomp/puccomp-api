# 0001 — Monólito modular com Spring Modulith

- **Status:** aceito
- **Data:** 2026-07-13
- **Decisores:** squad PUC COMP

## Contexto e problema

A PUC COMP API precisa cobrir várias capacidades de negócio de uma EJ (gestão de
membros, cargos, autorização, onboarding e, futuramente, finanças e
recrutamento) que evoluem em ritmos diferentes. Queremos fronteiras claras entre
essas capacidades — para que o código não vire uma bola de lama — sem pagar o
custo operacional de um sistema distribuído, dado que o time é pequeno e o
projeto é mantido por membros de uma EJ com rotatividade.

## Opções consideradas

- **Monólito em camadas** — organizar por camada técnica (`controller`,
  `service`, `repository`). Simples, mas o acoplamento se espalha: uma mudança de
  negócio atravessa todas as camadas e nada impede um serviço de chamar outro
  arbitrariamente.
- **Microsserviços** — um serviço por capacidade. Fronteiras fortíssimas, porém
  com custo alto de infra, deploy, observabilidade e consistência distribuída —
  desproporcional ao tamanho do time e à maturidade do projeto.
- **Monólito modular com Spring Modulith** — uma única aplicação dividida em
  módulos por capacidade de negócio, com as fronteiras **verificadas em tempo de
  teste**.

## Decisão

Adotamos o **monólito modular com Spring Modulith**.

O sistema é organizado por **capacidade de negócio** (`organization`,
`identity`, `authorization`, ...), não por entidade nem por camada técnica. O
teste de design é: _"consigo explicar esse módulo sem mencionar os outros?"_.
Dentro de um módulo, os tipos colaboram à vontade; **entre** módulos só se acessa
o que o outro publica (controllers e DTOs) — implementação (`service`,
`repository`) é *package-private*.

O que desempatou frente às camadas foi a **verificação automática**:
`ModularityTests.verify()` quebra o build se um módulo acessar o interno de
outro. A fronteira deixa de ser uma convenção que se erode e passa a ser uma
regra executável. Frente aos microsserviços, o desempate foi o **custo
operacional**: mantemos um único deploy e transações locais, e a modularização já
nos daria o caminho de extração futura caso uma capacidade justifique virar
serviço.

## Consequências

- 🟢 **Boa:** fronteiras entre capacidades são garantidas pelo build, não pela
  disciplina — impossível "pular a janela" sem o teste falhar.
- 🟢 **Boa:** deploy, transações e observabilidade permanecem simples (um
  processo, um banco).
- 🟢 **Boa:** a modularização abre caminho para extrair um módulo em serviço no
  futuro, se e quando fizer sentido.
- 🔴 **Ruim:** exige disciplina no desenho dos módulos por capacidade (e não por
  entidade) — o erro clássico de separar por entidade gera acoplamento espalhado.
- 🔴 **Ruim:** comunicação entre módulos passa por contratos explícitos, o que
  adiciona cerimônia frente a chamar um serviço diretamente.
- ⚪ **Neutra:** os módulos transversais `shared` e `config` são declarados
  `OPEN` (sem encapsulamento) — usados por todos por design.
