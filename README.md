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

Iremos usar o Docker Compose para os serviços locais. Os comandos específicos de frontend e backend irão estar documentados nos respectivos diretórios.

Consulte:

- [Documentação técnica](docs/doc.md)
- [Arquitetura completa na Wiki](https://github.com/WilliamSoares21/event-book/wiki/Arquitetura-Inicial-Event-Book)
- [Convenções de contribuição e branches](docs/contributing.md)

## Status

MVP em desenvolvimento. A estrutura inicial do monorepo está sendo consolidada antes da evolução das funcionalidades.
