# 0002 — Arquitetura de armazenamento de arquivos (S3)

- **Status:** proposto
- **Data:** 2026-07-24
- **Decisores:** Guilherme Meyer

## Contexto e problema

A plataforma precisa guardar dois tipos de arquivo com níveis de acesso distintos,
sem misturá-los: **arquivos públicos de projetos** (visíveis a qualquer visitante) e
**documentos privados dos tenants** (internos de cada EJ). O sistema é multi-tenant,
então "privado" ainda precisa isolar um tenant do outro. Não há nada de storage no
código hoje, então esta decisão define a arquitetura do zero: como separar os arquivos,
como a aplicação acessa o S3 e como os arquivos públicos chegam ao navegador.

Esta decisão depende da [#2](https://github.com/puccomp/puccomp-api/issues/2), que cria
o grupo IAM `CloudOps` para as pessoas operarem a AWS.

## Opções consideradas

Quatro eixos, cada um com suas alternativas:

- **Entrega dos públicos:** bucket com leitura pública direta, ou bucket fechado servido
  por uma CDN (CloudFront).
- **Isolamento dos privados:** um bucket por tenant, ou um único bucket privado com
  prefixo por tenant (`{tenantId}/...`).
- **Transferência de arquivos:** o arquivo trafega pela API, ou URLs pré-assinadas (o
  cliente sobe e baixa direto no S3).
- **Identidade da aplicação na AWS:** reusar o IAM das pessoas (o `CloudOps` da #2), ou um
  IAM próprio da aplicação.

## Decisão

Adotamos a seguinte combinação:

- **Públicos: bucket fechado + CloudFront (OAC)**, com `static.puccomp.com.br` apontando
  para a distribuição. O que desempatou frente à leitura pública direta foi não expor o
  bucket e ganhar cache, HTTPS e um ponto único de configuração.
- **Privados: um único bucket privado com prefixo por tenant**, acesso sempre mediado pela
  API. O que desempatou frente ao bucket por tenant foi a escala (a conta AWS tem limite de
  buckets) e reaproveitar o isolamento por tenant que a aplicação já faz no banco.
- **Transferência: URLs pré-assinadas.** O que desempatou foi não passar o arquivo pelo
  servidor da API e amarrar a autorização a um objeto e uma operação específicos, por tempo
  curto.
- **Identidade: um IAM próprio da aplicação**, de menor privilégio, separado do `CloudOps`.
  O que desempatou foi manter sistema e pessoa com identidades separadas, evitando dar à
  aplicação os poderes amplos de um operador humano.

Os detalhes concretos (nomes de bucket, políticas IAM em JSON, configurações e a estratégia
de CloudFront/DNS) ficam em [architecture/armazenamento-s3.md](../architecture/armazenamento-s3.md).
A criação do IAM próprio da aplicação fica como card futuro, já que a #2 optou por não criar
múltiplos IAM nesta fase; enquanto isso, esta decisão documenta as permissões de S3 do
`CloudOps` pelo menor privilégio.

## Consequências

- 🟢 **Boa:** público e privado nunca se misturam, e o bucket público nunca fica exposto
  diretamente (só o CloudFront lê).
- 🟢 **Boa:** o isolamento por prefixo escala para muitas EJs sem provisionar infra por tenant.
- 🟢 **Boa:** uploads e downloads não carregam banda nem memória do servidor da API.
- 🔴 **Ruim:** o isolamento dos privados depende da lógica da API estar correta, já que a
  aplicação precisa sempre usar o prefixo do tenant certo ao gerar as URLs.
- 🔴 **Ruim:** o fluxo no cliente fica mais elaborado (pedir a URL, depois subir ou baixar)
  frente a mandar tudo pela API.
- ⚪ **Neutra:** a aplicação passa a exigir um IAM próprio, o que adiciona um card de infra,
  mas é o alvo correto de menor privilégio.
