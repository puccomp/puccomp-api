# PUC COMP REST API

**PUC COMP** é uma empresa júnior da [PUC-MG](https://www.pucminas.br/destaques/Paginas/default.aspx), especializada em desenvolvimento de software sob demanda, operando essencialmente como uma _software house_. A organização é composta por **Membros**, que são estudantes matriculados em cursos do ICEI (Instituto de Ciências Exatas e Informática) e contribuem ativamente para a empresa. Cada Membro possui um **Cargo** específico, definindo sua função designada dentro da empresa. Os Membros colaboram em **Projetos**, que são soluções personalizadas desenvolvidas pela empresa para resolver problemas específicos enfrentados por seus clientes. Para garantir inovação e excelência em suas soluções, a COMP utiliza uma variedade de **Tecnologias** ao longo de seu processo de desenvolvimento de projetos, sempre buscando ultrapassar limites e entregar produtos de alta qualidade.

## Desenvolvimento

Para desenvolver a aplicação localmente, utilizamos Docker Compose para simplificar o ambiente de desenvolvimento e garantir consistência entre diferentes máquinas.

### Executando o ambiente de desenvolvimento

```bash
git clone 'https://github.com/puccomp/puccomp-api' 

cd puccomp-api

cp .env.example .env # adicione variáveis de ambiente

# inicie os serviços/containers
docker-compose up -d  # -d (ou --detach) recuperar o controle do terminal após execeutar o comando

# crie as tabelas no postgresql
docker-compose exec app npx prisma migrate dev

# popular o banco inicialmente
docker-compose exec app npm run prisma:seed
```

- Hot reload habilitado através de bind mounts (`./src` e `./prisma`)
- Dados persistidos em volume nomeado (`pgdata`)

### Comandos úteis

```bash
# vizualizar logs
docker-compose logs app -f # -f (ou --follow) o terminal fica "conectado" ao fluxo de logs

# executar comandos no container da aplicação
docker-compose exec app npm run dev 

# executar migrações do Prisma
docker-compose exec app npx prisma migrate dev

# parar os serviços
docker-compose down 

# parar e remover volumes (apaga dados do banco)
docker-compose down -v
```
