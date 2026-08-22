# Armazenamento de arquivos (S3)

Referência de implementação da arquitetura de armazenamento. O **porquê** das escolhas está
no [ADR 0002](../adr/0002-armazenamento-s3.md); aqui ficam os detalhes concretos.

> Nada neste documento foi criado na AWS ainda. É a base para os cards de implementação
> (ver "Próximos passos").

## Visão geral

```mermaid
flowchart LR
    subgraph Publico[Fluxo publico]
        V[Visitante] -->|https| CF[CloudFront]
        CF -->|OAC| SB[(Bucket publico<br/>puccomp-static-ENV)]
        DNS[static.puccomp.com.br] -.aponta.-> CF
    end
    subgraph Privado[Fluxo privado]
        C[Cliente autenticado] -->|1. pede URL| API[puccomp-api]
        API -->|2. gera URL pre-assinada| C
        C -->|3. upload/download direto| PB[(Bucket privado<br/>puccomp-private-ENV<br/>prefixo por tenant)]
    end
    API -.credencial propria.-> PB
```

- **Público:** o objeto fica em um bucket fechado; o CloudFront é a única forma de ler; o
  domínio `static.puccomp.com.br` aponta para a distribuição.
- **Privado:** o cliente nunca acessa o bucket direto; a API gera uma URL pré-assinada de
  curta duração para um objeto específico, e o cliente sobe ou baixa direto no S3.

## Estrutura de buckets

- **Região:** `sa-east-1` (São Paulo).
- **Ambientes separados:** um conjunto de buckets por ambiente (dev e prod).

| Ambiente | Bucket público        | Bucket privado         |
|----------|-----------------------|------------------------|
| prod     | `puccomp-static-prod` | `puccomp-private-prod` |
| dev      | `puccomp-static-dev`  | `puccomp-private-dev`  |

Layout de chaves (exemplos):

- Público: `projetos/{projectSlug}/{assetId}.png`
- Privado: `{tenantId}/documentos/{documentId}-{nome}.pdf` (ex.: `ej-comp/documentos/42-contrato.pdf`)

## Configurações recomendadas dos buckets

- **Block Public Access: ligado** nos dois tipos de bucket. O bucket público continua
  fechado; o CloudFront lê via OAC por uma bucket policy específica (ver seção CloudFront),
  o que não é acesso público e convive com o Block Public Access ligado.
- **Criptografia em repouso:** SSE-S3 (AES-256), padrão e sem custo. Alternativa SSE-KMS, se
  no futuro precisarmos de controle de chave e auditoria por chave.
- **Versionamento:** ligado no **bucket privado** (recuperar exclusões acidentais).
- **CORS:** necessário nos buckets que recebem upload do navegador via URL pré-assinada.
  Liberar apenas as origens do front-end:

  ```json
  [
    {
      "AllowedOrigins": ["https://app.puccomp.com.br"],
      "AllowedMethods": ["GET", "PUT", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
  ```

  (As origens exatas do front-end são um item a confirmar.)
- **Validade das URLs pré-assinadas:** curta, sugestão de 5 a 15 minutos.
- **Ciclo de vida:** opcional, por exemplo expirar versões antigas do bucket privado após N
  dias para controlar custo.

## Permissões IAM (menor privilégio)

### CloudOps (operadores, issue #2), permissões de S3

Permite ao grupo `CloudOps` criar e configurar os buckets do projeto, restrito aos buckets
com prefixo `puccomp-` (não a todo o S3 da conta).

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListarBucketsDaConta",
      "Effect": "Allow",
      "Action": ["s3:ListAllMyBuckets", "s3:GetBucketLocation"],
      "Resource": "*"
    },
    {
      "Sid": "CriarBucketsDoProjeto",
      "Effect": "Allow",
      "Action": "s3:CreateBucket",
      "Resource": "arn:aws:s3:::puccomp-*"
    },
    {
      "Sid": "ConfigurarBucketsDoProjeto",
      "Effect": "Allow",
      "Action": [
        "s3:PutBucketPublicAccessBlock", "s3:GetBucketPublicAccessBlock",
        "s3:PutBucketPolicy", "s3:GetBucketPolicy", "s3:DeleteBucketPolicy",
        "s3:PutEncryptionConfiguration", "s3:GetEncryptionConfiguration",
        "s3:PutBucketVersioning", "s3:GetBucketVersioning",
        "s3:PutBucketCORS", "s3:GetBucketCORS",
        "s3:PutLifecycleConfiguration", "s3:GetLifecycleConfiguration",
        "s3:PutBucketOwnershipControls", "s3:GetBucketOwnershipControls",
        "s3:PutBucketTagging", "s3:GetBucketTagging",
        "s3:ListBucket"
      ],
      "Resource": "arn:aws:s3:::puccomp-*"
    },
    {
      "Sid": "InspecionarObjetosParaVerificacao",
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": "arn:aws:s3:::puccomp-*/*"
    }
  ]
}
```

Notas:

- Gravar e apagar o **conteúdo** dos objetos é responsabilidade da aplicação, não do operador.
  Se um operador precisar esvaziar um bucket para excluí-lo, adiciona-se pontualmente
  `s3:PutObject`/`s3:DeleteObject`.
- Este documento cobre a parte de **S3**. As permissões de CloudFront, ACM e Route 53 que o
  `CloudOps` também precisa fazem parte do escopo mais amplo do `CloudOps` (#2), não são de S3.

### Aplicação (alvo, card futuro), permissões de S3

Identidade própria da aplicação, restrita às operações de objeto necessárias em runtime:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "GravarAssetsPublicos",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::puccomp-static-${env}/*"
    },
    {
      "Sid": "GerenciarDocumentosPrivados",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::puccomp-private-${env}/*"
    }
  ]
}
```

Notas:

- No bucket público a aplicação só **grava** (a leitura pública é feita pelo CloudFront).
- No bucket privado a aplicação **grava e lê**, porque gera as URLs pré-assinadas de upload e
  de download. Uma URL pré-assinada nunca ultrapassa a política da própria aplicação.
- A aplicação atende todos os tenants, então precisa acessar todos os prefixos. O isolamento
  por tenant é garantido na **lógica da API**, que só gera URL para o prefixo do tenant correto.
- Se a aplicação rodar em compute da AWS (EC2/ECS), o ideal é uma **role** assumida pelo
  serviço, em vez de chaves de acesso fixas.

## Estratégia de CloudFront e DNS (`static.puccomp.com.br`)

1. **Distribuição CloudFront** com o bucket `puccomp-static-prod` como origem, usando
   **Origin Access Control (OAC)**. O bucket permanece fechado; só o CloudFront lê, via uma
   bucket policy que autoriza o serviço do CloudFront apenas para aquela distribuição:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PermitirLeituraViaCloudFront",
         "Effect": "Allow",
         "Principal": { "Service": "cloudfront.amazonaws.com" },
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::puccomp-static-prod/*",
         "Condition": {
           "StringEquals": {
             "AWS:SourceArn": "arn:aws:cloudfront::<ACCOUNT_ID>:distribution/<DISTRIBUTION_ID>"
           }
         }
       }
     ]
   }
   ```

2. **Domínio e certificado:** `static.puccomp.com.br` entra como nome de domínio alternativo
   (CNAME) na distribuição. O HTTPS vem de um certificado do **ACM emitido em `us-east-1`** (o
   CloudFront exige o certificado nessa região, mesmo com os buckets em `sa-east-1`).

3. **DNS:** aponta-se `static.puccomp.com.br` para o domínio da distribuição. O caminho depende
   de **onde o domínio `puccomp.com.br` é gerenciado** (item a confirmar):
   - **Route 53 (AWS):** registro **ALIAS** (A/AAAA) de `static` para a distribuição.
   - **DNS externo** (Registro.br, Cloudflare, etc.): registro **CNAME** de `static` para o
     domínio da distribuição (`dxxxxxxxx.cloudfront.net`). Funciona por ser subdomínio.

## Itens em aberto (a confirmar na implementação)

- **Onde o DNS de `puccomp.com.br` é gerenciado** (Route 53 ou externo). Define o passo 3 acima.
- **Origens exatas do front-end** para o CORS.
- **Conta AWS e Account ID** para preencher os ARNs de exemplo.
- Se a aplicação usará **role** (compute AWS) ou **usuário com chaves** (fora da AWS).

## Próximos passos (cards futuros)

- Anexar a política de S3 do `CloudOps` (acima) ao grupo, na #2.
- Card para criar o **IAM próprio da aplicação**.
- Card para **criar os buckets** com as configurações desta página.
- Card para **criar a distribuição CloudFront**, o certificado ACM e o registro DNS.
- Card para implementar na API o **serviço de armazenamento** (URLs pré-assinadas, layout de
  chaves por tenant).

## Critérios de aceite da #20

- **Separação público/privado definida:** Visão geral, Estrutura de buckets, e o ADR 0002.
- **Permissões S3 do IAM da #2 por menor privilégio:** seção CloudOps acima.
- **Estratégia de CloudFront e DNS documentada:** seção CloudFront e DNS.
- **Nenhum recurso criado nesta etapa:** aviso no topo desta página.
