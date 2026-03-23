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
docker compose -f docker-compose.dev.yml up -d  # -d (ou --detach) recuperar o controle do terminal após execeutar o comando

# crie as tabelas no postgresql
docker compose -f docker-compose.dev.yml exec app npx prisma migrate dev

# popular o banco inicialmente
docker compose -f docker-compose.dev.yml exec app npm run prisma:seed
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
docker compose -f docker-compose.dev.yml logs app -f # -f (ou --follow) o terminal fica "conectado" ao fluxo de logs

# executar comandos no container da aplicação
docker compose -f docker-compose.dev.yml exec app npm run dev

# executar migrações do Prisma
docker compose -f docker-compose.dev.yml exec app npx prisma migrate dev

# parar os serviços
docker compose -f docker-compose.dev.yml down

# parar e remover volumes (apaga dados do banco)
docker compose -f docker-compose.dev.yml down -v
```

## Produção

O deploy é feito automaticamente via **GitHub Actions** a cada push na branch `main`. O workflow:

1. Builda a imagem Docker usando `Dockerfile.prod`
2. Faz push para o GitHub Container Registry (`ghcr.io`)
3. Conecta na VPS via SSH e executa `docker compose pull api && docker compose up -d api`

As migrações do Prisma são aplicadas automaticamente na inicialização do container.

### Pré-requisitos na VPS

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-plugin nginx certbot python3-certbot-nginx
sudo usermod -aG docker $USER
# reconecte ao SSH para aplicar o grupo
```

### Setup inicial na VPS (só uma vez)

```bash
# autenticar no GitHub Container Registry
echo SEU_PAT | docker login ghcr.io -u SEU_USUARIO --password-stdin

# criar o arquivo de configuração no diretório central
mkdir -p /var/www/puccomp
cp .env.example /var/www/puccomp/puccomp-api/.env
nano /var/www/puccomp/puccomp-api/.env  # preencher todas as variáveis

# subir pela primeira vez
cd /var/www/puccomp
docker compose up -d

# popular o banco na primeira execução
docker compose exec api npx tsx prisma/seed.ts
```

### Variáveis de ambiente para produção

| Variável | Descrição |
|---|---|
| `NODE_ENV` | `production` |
| `PORT` | `8080` |
| `BASE_URL` | URL pública da API (ex: `https://api.seudominio.com`) |
| `DATABASE_URL` | `postgresql://POSTGRES_USER:POSTGRES_PASSWORD@db:5432/POSTGRES_DB` |
| `POSTGRES_USER` | Usuário do banco |
| `POSTGRES_PASSWORD` | Senha forte do banco |
| `POSTGRES_DB` | Nome do banco (ex: `puccomp`) |
| `JWT_SECRET_KEY` | String aleatória longa |
| `AWS_*` | Credenciais reais do S3 |
| `EMAIL_*` | SMTP real (ex: SendGrid, Amazon SES) |
| `FRONTEND_URLS` | Origens permitidas pelo CORS |

### Configurando o nginx no host

Copie o arquivo de configuração de referência:

```bash
sudo cp nginx/puccomp-api.conf /etc/nginx/sites-available/puccomp-api
# edite server_name com seu domínio
sudo nano /etc/nginx/sites-available/puccomp-api

sudo ln -s /etc/nginx/sites-available/puccomp-api /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### SSL com Let's Encrypt

```bash
sudo certbot --nginx -d seudominio.com
```

O certbot atualiza o arquivo de configuração do nginx automaticamente com os certificados.