# 0004 — Currículos privados no módulo files

- **Status:** proposto
- **Data:** 2026-09-05

## Contexto

O recrutamento recebe inscrições anônimas. É necessário receber currículos PDF sem
expor documentos pessoais e compartilhar armazenamento com futuros módulos.
O ADR 0002 propõe upload e download diretos por URLs pré-assinadas; ainda não foi aceito.

## Decisão

Criar `files` como módulo fechado com contrato `FileService`. Recrutamento guarda
somente `cvFileId`, autoriza o negócio e solicita metadados/URLs em lote. A interface
interna `ObjectStorage` isola o SDK S3. Nenhuma entidade de outro módulo é importada.

Para estes PDFs pequenos, enviar candidatura e arquivo em um único multipart pela API.
Isso adapta **o upload de currículos** da proposta 0002: permite limitar bytes, validar
conteúdo e executar antivírus antes de gravar, sem sessão anônima de upload separada.
Mantêm-se bucket privado, prefixo por tenant e download S3 pré-assinado.

JSON sem currículo continua válido. Multipart exige `application` JSON e `cv` PDF.
O comprovante público não contém documento nem URL. Somente a listagem autorizada
com `recruitment:read` fornece `cv.download_url` e `cv.download_expires_at`.

Os metadados usam JDBC interno com tenant explícito em todas as operações de usuário.
Uma reserva `PENDING` é confirmada em transação independente **antes** do PUT. A mudança
para `READY` e a candidatura compartilham a mesma transação. Reservas com mais de 24h
são limpas por tarefa global de infraestrutura, com bloqueio de linhas e retry.

## Consequências

- PDFs são privados apesar do endpoint de envio ser público.
- A API consome banda durante upload e faz validação síncrona, limitada a quatro uploads simultâneos por instância.
- ClamAV é obrigatório para uploads habilitados; erro ou indisponibilidade bloqueiam o envio.
- O módulo não implementa pastas, edição, publicação pública ou tratamento de imagens.
- Upload direto pode ser adicionado posteriormente com quarentena e validação de uma versão imutável.
- Processamento ocorre no processo da API; os limites existentes não equivalem a uma sandbox.
  Para cargas maiores ou parsers adicionais, isolar processamento em workers com limites do sistema operacional.

Configuração e contrato: [arquivos](../architecture/arquivos.md).
