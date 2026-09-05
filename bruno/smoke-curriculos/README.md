# Smoke de currículos

Execute esta pasta em ordem no Runner do Bruno. Variáveis necessárias:

- `base_url`: API local/de teste.
- `smoke_org_slug`: slug de uma EJ ativa.
- `smoke_owner_email` e `smoke_owner_password`: conta dona dessa EJ (ambiente privado).

O storage e o ClamAV precisam estar habilitados. Os PDFs artificiais estão em
`bruno/fixtures`. A execução cria um processo e uma candidatura, além de disparar
emails; use somente dados e serviços de teste. Nenhum dado existente é excluído.

Com Bruno CLI instalado, a partir de `bruno/`:

```sh
bru run smoke-curriculos --env seu-ambiente-de-teste
```

Os nove requests verificam login, criação e abertura do processo, envio anônimo de PDF,
DTO privado com URL/expiração, download S3, rejeição de PDF falso, bloqueio da listagem
anônima e rejeição de duplicação. As variáveis geradas existem somente durante o run.

Também é possível executar contra a infraestrutura descartável do teste de integração,
sem configurar AWS e sem enviar emails externos:

```sh
BRUNO_SMOKE_CLI=/caminho/absoluto/para/bru ./gradlew test --tests '*CvSubmissionEndToEndTest' --rerun-tasks
```

Esse comando roda na raiz do repositório, usa PostgreSQL/MinIO reais em Testcontainers e
simula os transportes de antivírus e SMTP. O protocolo ClamAV tem testes próprios.
O resultado do Bruno fica em `build/bruno-smoke.log` e uma falha do smoke falha o teste Java.
