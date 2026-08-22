# PUC COMP API

Plataforma **ERP SaaS multi-tenant e AI-first** para Empresas Juniores (EJs).
Um monólito modular, em Java moderno e Spring Boot, que expõe uma API RESTful e
um servidor MCP para que EJs gerenciem membros, cargos, autorização, onboarding e
demais operações internas — com isolamento entre organizações no mesmo ambiente
compartilhado.

## Como executar

Em `dev`/local (perfil default) não é necessário configurar variáveis de ambiente.

**Pré-requisitos**:
- Docker
- JDK 25

```bash
docker compose up -d      # sobe postgres + mailpit em background (-d = detached)
./gradlew bootRun         # sobe a API no perfil dev (http://localhost:8080)
```

O `-d` (detached) deixa os contêineres rodando em segundo plano — você segue
usando o terminal. Use `docker compose logs -f` para acompanhar e
`docker compose down` para derrubá-los.

No primeiro boot com o banco vazio, o seeder cria a EJ `ej-comp` e contas de
teste. O passo a passo completo (contas, portas, Mailpit) está no
[CONTRIBUTING.md](CONTRIBUTING.md#ambiente-local).

### Comandos principais

```bash
./gradlew bootRun     # roda a API (perfil dev)
./gradlew test        # testes — sobe Postgres real via Testcontainers (precisa do Docker)
./gradlew build       # compila, roda testes e gera o .jar em build/libs
./gradlew clean       # limpa artefatos de build
```

### Observabilidade (opcional)

Métricas ficam expostas em `/actuator/prometheus`. Para visualizá-las em
dashboards, suba a stack de Prometheus + Grafana (arquivo compose separado):

```bash
docker compose -f compose.observability.yml up -d   # prometheus + grafana
```

Grafana em http://localhost:3000 (`admin` / `admin`); Prometheus em
http://localhost:9090.

## Explorando a API

| Recurso | URL (perfil `dev`) |
|---|---|
| Swagger UI | http://localhost:8080/docs |
| Especificação OpenAPI | http://localhost:8080/v3/api-docs |
| Collection Bruno | [`bruno/`](bruno) |

O contrato é gerado a partir do código pelo Springdoc — ver
[docs/README.md](docs/README.md#contrato-da-api).

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/](docs/README.md) | Índice e estratégia de documentação |
| [docs/architecture/c4.md](docs/architecture/c4.md) | Arquitetura no C4 Model (contexto, contêineres, componentes) |
| [docs/adr/](docs/adr/) | Architecture Decision Records — o "porquê" das decisões |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Fluxo de trabalho, padrões de código, setup e testes |

## Contribuindo

Leia o [CONTRIBUTING.md](CONTRIBUTING.md) antes da primeira PR: ele define Git
Flow, Conventional Commits (em português), padrões de código e a arquitetura
modular. Este repositório será público — o histórico reflete a imagem da
organização.
