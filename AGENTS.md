## Overview

A PUC COMP API é uma plataforma ERP SaaS multi-tenant e AI-first voltada para Empresas Juniores (EJs). O sistema é um monolito modular que disponibiliza uma API RESTful e um servidor MCP, desenvolvidos com Java moderno e Spring Boot, permitindo que EJs de diferentes áreas gerenciem processos como finanças, recrutamento, gestão de membros, cargos e outras operações internas em um ambiente compartilhado com isolamento entre organizações.

## Documentos de Referência

Abaixo, segue alguns arquivos que em determinados momentos podem ser úteis para consultas:

- [CONTRIBUTING.md](CONTRIBUTING.md): Define o fluxo de trabalho, padrões de código e convenções do projeto.
- [docs/](docs/README.md): Índice e estratégia da documentação técnica e de arquitetura.
- [docs/architecture/c4.md](docs/architecture/c4.md): Abordagem de arquitetura (C4 Model); Nível 3 gerado pelo Spring Modulith.
- [docs/adr/](docs/adr/README.md): Architecture Decision Records — o "porquê" das decisões técnicas.

## Instruções

- Sempre que modificar o contrato da API, altere a [collection](./bruno) para refletir as mudanças.