# Arquivos privados e currículos

Implementação: módulo `files`; decisão em [ADR 0004](../adr/0004-curriculos-privados.md).

## Contrato

`POST /v1/public/{orgSlug}/processes/{processId}/applications` aceita:

- `application/json`: inscrição sem currículo, compatível com o contrato anterior.
- `multipart/form-data`: exatamente `application` com Content-Type `application/json`
  e `cv` com Content-Type `application/pdf`. O JSON tem os mesmos campos da inscrição.

```sh
curl --fail-with-body 'http://localhost:8080/v1/public/ej-comp/processes/PROCESS_ID/applications' \
  -F 'application={"full_name":"Ana Lima","email":"ana@example.com","phone":"31999990000","course":"Computação","privacy_consent":true};type=application/json' \
  -F 'cv=@curriculo.pdf;type=application/pdf'
```

No navegador, `FormData.append('application', new Blob([JSON.stringify(application)],
{ type: 'application/json' }))` e `FormData.append('cv', file)`. Não configure manualmente
o Content-Type da requisição: o navegador precisa gerar o boundary.

O comprovante público permanece `{ id, submitted_at }`.
`GET /v1/recruitment/processes/{processId}/applications` exige `recruitment:read`
e acrescenta `cv` (null sem currículo):

```json
{
  "id": "uuid-do-arquivo",
  "filename": "curriculo.pdf",
  "content_type": "application/pdf",
  "size": 18200,
  "download_url": "https://bucket.s3.sa-east-1.amazonaws.com/...?...",
  "download_expires_at": "2026-09-05T15:05:00Z"
}
```

Faça GET em `download_url` sem bearer da API. Consulte novamente a listagem para renovar.
URLs são credenciais temporárias: não persistir, registrar em logs nem incluir em analytics.
A listagem e o objeto usam `Cache-Control: private, no-store`; o download força attachment
e `application/octet-stream`. Revogar uma permissão não invalida URLs já emitidas;
elas continuam utilizáveis até expirar (ou até a credencial AWS expirar, se antes).

## Configuração

Por padrão `FILES_ENABLED=false`: JSON continua funcionando; upload responde 503.
Para habilitar:

| Variável | Valor |
|---|---|
| `FILES_ENABLED` | `true` |
| `FILES_BUCKET` | bucket privado **do ambiente** |
| `AWS_REGION` | `sa-east-1` por padrão |
| `CLAMAV_HOST` / `CLAMAV_PORT` | daemon clamd acessível; localhost / 3310 por padrão |
| `FILES_ENDPOINT` | vazio na AWS; URL do servidor S3 compatível para desenvolvimento |
| `FILES_PATH_STYLE` | `true` para MinIO, `false` na AWS |
| `FILES_MAX_PAGES` | páginas aceitas por PDF; `20` por padrão |
| `FILES_DECOMPRESSED_PER_PAGE` | orçamento de inflação por página; `32MB` por padrão |
| `FILES_MAX_DECOMPRESSED` | teto absoluto de inflação por arquivo; `256MB` por padrão |

O SDK usa a cadeia padrão de credenciais AWS: role da aplicação em produção, ou
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` no ambiente local. Nunca versionar segredos.
Permissões: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` no bucket privado.
O serviço não cria buckets. Use Block Public Access e criptografia em repouso.
O endpoint configurado também aparece nas URLs de download: precisa ser acessível ao navegador.

As URLs duram 300 segundos (`puccomp.files.download-ttl-seconds`, máximo 900).
Configuração de bucket/CDN mais ampla permanece em [armazenamento S3](armazenamento-s3.md).
Esta implementação não publica arquivos nem usa o bucket estático.

## Verificação

`./gradlew test` inclui validação de PDF, protocolo do antivírus, isolamento de tenant,
falhas e limpeza de storage e o teste completo de candidatura + S3 + destinatários dos emails.
Este último usa MinIO real e transportes simulados de scanner/SMTP; não depende de conta AWS.
Os [smoke tests Bruno](../../bruno/smoke-curriculos/README.md) podem rodar nessa mesma infraestrutura.

## Validação e operação

- Limite HTTP de 5 MiB por parte e 6 MiB por requisição; leitura do arquivo também limitada.
  Os 5 MiB não são configuráveis: o valor é compartilhado por `spring.servlet.multipart.max-file-size`,
  pela leitura no `PdfValidator` e pelo `CHECK` de `stored_files.size` na V12. Mudar um sem os
  outros troca um 413 claro por violação de constraint no meio da transação.
- Nome de até 120 caracteres, sem caminhos, controles, aspas ou `..`; chave S3 gerada por UUID.
- MIME, assinatura inicial/final e parsing PDF estrito; extensão não basta.
- Até `FILES_MAX_PAGES` páginas; proíbe criptografia, scripts, anexos embutidos, XFA e ações
  perigosas. `AcroForm` e `OpenAction` benignos passam: o que é bloqueado é o conteúdo dentro
  deles (`JS`, `XFA`, ações `JavaScript`/`Launch`/`SubmitForm`), não a chave em si — recusar a
  chave rejeitava documento comum assinado ou exportado com campos de formulário vazios.
- Links só com esquemas HTTP, HTTPS ou mailto. Não são visitados pelo servidor.
- Antivírus ClamAV antes do parser e da gravação; só `stream: OK` libera.
- Limite de objetos e orçamento de conteúdo descomprimido durante a inspeção estrutural. O
  orçamento acompanha as páginas reais (`FILES_DECOMPRESSED_PER_PAGE`) com teto absoluto
  (`FILES_MAX_DECOMPRESSED`): uma página A4 escaneada a 300 dpi RGB já descomprime ~26 MiB,
  então um valor fixo baixo recusava currículo digitalizado dentro do limite de 5 MiB.
- Quatro uploads simultâneos por instância; deadline total de 20s no scanner e no cliente S3.
- O timeout da inspeção não é um isolamento de CPU/memória do parser. Não se promete
  que parsing e antivírus detectem todas as ameaças; PDFs continuam conteúdo não confiável.

Configure clamd com bases atualizadas, `ScanPDF yes`, `StreamMaxLength` de pelo menos 5 MiB,
`MaxFileSize` de pelo menos 5 MiB, limites finitos para descompressão/recursão/tempo e
`AlertExceedsMax yes` para recusar inspeções incompletas. Restrinja a porta 3310 à rede
privada: o protocolo clamd não autentica nem cifra o tráfego.

O endpoint é anônimo: o gateway de produção deve limitar taxa por IP/rota, conexões,
tempo de upload e tamanho de corpo. O limite de concorrência da aplicação não substitui
rate limiting nem quota de armazenamento; monitore volume e custo do bucket.

Antivírus e PUT rodam **fora** de transação (`FileService.stage`); só o `READY` entra na transação da candidatura (`FileService.confirm`), então nenhuma conexão do pool fica presa durante os segundos de rede. Processo fechado e inscrição duplicada são recusados antes do `stage`, para não gastar antivírus e S3 num envio que já seria recusado.
O arquivo só fica `READY` junto com a transação da candidatura. Após falha, reservas
`PENDING` de mais de 24h são apagadas em lotes de 20 a cada hora; falhas de exclusão
permanecem para retry. A consulta global dessa tarefa é exclusiva de manutenção e não
é acessível via HTTP. Inscrições existentes não são apagadas pela rotina.

Em buckets versionados, DELETE cria um marcador: configure lifecycle de versões
não atuais e marcadores expirados para remover fisicamente bytes órfãos. A exclusão de
currículos confirmados e a política de retenção das candidaturas são uma evolução separada.

Fontes: [OWASP Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html),
[ClamD INSTREAM](https://docs.clamav.net/manual/Usage/ClamdProtocol.html),
[PDFBox Security](https://pdfbox.apache.org/security.html).
