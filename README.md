# PUC COMP REST API

**PUC COMP** é uma empresa júnior da [PUC-MG](https://www.pucminas.br/destaques/Paginas/default.aspx), especializada em desenvolvimento de software sob demanda, operando essencialmente como uma _software house_. A organização é composta por **Membros**, que são estudantes matriculados em cursos do ICEI (Instituto de Ciências Exatas e Informática) e contribuem ativamente para a empresa. Cada Membro possui um **Cargo** específico, definindo sua função designada dentro da empresa. Os Membros colaboram em **Projetos**, que são soluções personalizadas desenvolvidas pela empresa para resolver problemas específicos enfrentados por seus clientes. Para garantir inovação e excelência em suas soluções, a COMP utiliza uma variedade de **Tecnologias** ao longo de seu processo de desenvolvimento de projetos, sempre buscando ultrapassar limites e entregar produtos de alta qualidade.

## Desenvolvimento

Para desenvolver a aplicação localmente, utilizamos Docker Compose para simplificar o ambiente de desenvolvimento e garantir consistência entre diferentes máquinas.

### Executando o ambiente de desenvolvimento

```bash
git clone 'https://github.com/puccomp/puccomp-api'

cd puccomp-api

cp .env.example .env # adicione variáveis de ambiente

sudo chmod +x localstack-init/create-bucket.sh

# inicie os serviços/containers
docker compose up -d  # -d (ou --detach) recuperar o controle do terminal após execeutar o comando

# crie as tabelas no postgresql
docker compose exec app npx prisma migrate dev

# popular o banco inicialmente
docker compose exec app npm run prisma:seed
```

- Hot reload habilitado através de bind mounts (`./src` e `./prisma`)
- Dados persistidos em volume nomeado (`pgdata`)

### Serviços de desenvolvimento

Em `NODE_ENV !== production`, a API usa automaticamente o **LocalStack** (S3) e o **MailHog** (e-mail) no lugar dos serviços reais.

| Serviço | Descrição | Endpoint |
|---------|-----------|----------|
| **LocalStack** — S3 API | Emula a AWS S3 localmente | `http://localhost:4566` |
| **LocalStack** — Health | Status dos serviços em execução (JSON) | `http://localhost:4566/_localstack/health` |
| **MailHog** — Web UI | Caixa de entrada dos e-mails capturados | `http://localhost:8025` |
| **MailHog** — API REST | Listagem e busca de mensagens | `http://localhost:8025/api/v2/messages` |

#### Inspecionando o LocalStack com rclone

Como alternativa ao AWS CLI, é possível usar o [rclone](https://rclone.org) para navegar pelos buckets do LocalStack.

Configure um remote uma única vez:

```bash
rclone config create localstack s3 \
  provider Other \
  endpoint http://localhost:4566 \
  access_key_id test \
  secret_access_key test \
  region us-east-1
```

Ou adicione manualmente em `~/.config/rclone/rclone.conf`:

```ini
[localstack]
type = s3
provider = Other
endpoint = http://localhost:4566
access_key_id = test
secret_access_key = test
region = us-east-1
```

Comandos úteis:

```bash
rclone lsd localstack:                          # listar buckets
rclone ls localstack:puccomp-uploads            # listar objetos do bucket
rclone copy ./arquivo.jpg localstack:puccomp-uploads/pasta/  # enviar arquivo
rclone deletefile localstack:puccomp-uploads/pasta/arquivo.jpg  # deletar objeto
```

### Comandos úteis

```bash
# vizualizar logs
docker compose logs app -f # -f (ou --follow) o terminal fica "conectado" ao fluxo de logs

# executar comandos no container da aplicação
docker compose exec app npm run dev 

# executar migrações do Prisma
docker compose exec app npx prisma migrate dev

# parar os serviços
docker compose down 

# parar e remover volumes (apaga dados do banco)
docker compose down -v
```
