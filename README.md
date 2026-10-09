# EventBook

EventBook é uma plataforma para gestão e consumo de eventos. O MVP contemplará a publicação de eventos e lotes, a reserva simulada de ingressos e a validação de acesso na portaria.

## Arquitetura

O projeto utiliza um monorepo com as seguintes camadas:

| Camada | Tecnologia | Ambiente previsto |
| --- | --- | --- |
| Frontend | Angular | Vercel |
| Backend | Java / Spring Boot | Oracle Cloud Infrastructure |
| Banco de dados | PostgreSQL | Supabase |
| Containers | Docker / Docker Compose | Local e OCI |
| CI/CD | GitHub Actions | GitHub |

O frontend se comunica com a API REST em Spring Boot por HTTPS. O backend acessa o PostgreSQL usando JPA/JDBC.

## Estrutura do repositório

```text
event-book/
├── .github/workflows/  # Automações do GitHub Actions
├── backend/            # API Java / Spring Boot (mantido pelo time de backend)
├── frontend/           # Aplicação Angular (mantida pelo time de frontend)
├── infra/              # Materiais de infraestrutura e execução
└── docs/               # Documentação técnica e links da Wiki
```

## Módulos planejados

Users, Events, Ticket Lots, Orders, Tickets, Check-ins e Event Staff.

Os perfis previstos são Comprador, Organizador e Staff, com permissões específicas por operação.

## Desenvolvimento

O ambiente local utiliza Docker Compose. O PostgreSQL pode ser executado
independentemente do backend:

1. Crie `./.env` na raiz do projeto (esse arquivo não deve ser versionado).
   Os valores abaixo são um exemplo, defina pelo menos uma senha para o
   PostgreSQL:

   ```env
   POSTGRES_DB=eventbook
   POSTGRES_USER=eventbook
   POSTGRES_PASSWORD=devpass
   POSTGRES_PORT=5432
   ```

   Caso a porta `5432` esteja ocupada no computador, altere apenas
   `POSTGRES_PORT` (por exemplo, para `5433`).

2. Suba o PostgreSQL:

   ```bash
   docker compose up -d db
   ```

   O banco estará disponível em `localhost:${POSTGRES_PORT}`. Para acessar com
   `pgcli`:

   ```bash
   pgcli -h localhost -p 5432 -U eventbook -d eventbook
   ```

   Ou, usando o cliente oficial do PostgreSQL:

   ```bash
   psql -h localhost -p 5432 -U eventbook -d eventbook
   ```

   Se você alterou `POSTGRES_PORT`, substitua `5432` nos comandos pela porta
   escolhida. Em ambos os casos, informe a senha definida em
   `POSTGRES_PASSWORD`.

O serviço `db` utiliza o volume nomeado `eventbook_data`, portanto os dados
persistem quando o container é parado. Para remover o container sem apagar os
dados, use `docker compose stop db`. O comando `docker compose down -v`
também remove o volume e apaga os dados persistidos.

O Compose já define a conexão que o backend deverá utilizar na rede Docker:
`DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Entretanto, a execução completa do
backend pelo Compose está pendente até que `backend/Dockerfile` seja criado e
integrado à branch principal. Por enquanto, a validação disponível neste
ambiente é a execução do PostgreSQL:

```bash
docker compose ps
docker compose logs db
```

Consulte:

- [Documentação técnica](docs/doc.md)
- [Arquitetura completa na Wiki](https://github.com/WilliamSoares21/event-book/wiki/Arquitetura-Inicial-Event-Book)
- [Convenções de contribuição e branches](docs/contributing.md)

## Status

MVP em desenvolvimento. A estrutura inicial do monorepo está sendo consolidada antes da evolução das funcionalidades.
