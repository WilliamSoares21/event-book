# EventBook

EventBook é uma plataforma para gestão e consumo de eventos. O MVP contempla a publicação de eventos e lotes, a reserva simulada de ingressos, a validação de acesso na portaria e a descoberta de eventos via catálogo público com pesquisa e filtros.

## Arquitetura

O projeto utiliza um monorepo com as seguintes camadas:

| Camada | Tecnologia | Versão | Deploy |
| --- | --- | --- | --- |
| Frontend | Angular | 22.0.0 | Vercel |
| Backend | Java / Spring Boot | 21 / 4.1.1 | Render |
| Banco de dados | PostgreSQL | 17 | Neon |
| Containers | Docker / Docker Compose | - | Local |
| CI/CD | GitHub Actions | - | GitHub |

O frontend se comunica com a API REST em Spring Boot por HTTPS. O backend acessa o PostgreSQL usando Spring Data JPA / Hibernate.

## Estrutura do repositório

```text
event-book/
├── .github/workflows/     # Automações do GitHub Actions (CI/CD)
├── backend/               # API Java / Spring Boot
│   ├── src/main/java/.../events/      # Domínio Event (entidade, DTOs, controllers, services)
│   ├── src/main/resources/db/migration/ # Flyway migrations (V1..V9)
│   └── pom.xml
├── frontend/
│   └── event-book-frontend/           # Aplicação Angular 22 (standalone, signals, zoneless)
│       ├── src/app/
│       │   ├── components/            # SearchBar, FilterPanel, EventCard, MenuSideBar, Toast
│       │   ├── model/                 # Interfaces TypeScript (Event, PageResponse, EventFilters)
│       │   ├── pages/                 # ShopEvents, EventsComponent, CreateEvents, DetailsEvents, Login, etc.
│       │   └── services/              # EventsService, SessionService, AuthService
│       ├── angular.json
│       └── package.json
├── infra/                 # Materiais de infraestrutura complementares
├── docs/                  # Documentação técnica
│   ├── architecture/phase-1/        # Especificação técnica Fase 1 (endereço estruturado + pesquisa/filtros)
│   └── architecture/phase-1/review/ # Revisão de consistência e decisões arquiteturais
├── docker-compose.yml     # Orquestração local (db, backend, frontend)
├── .env                   # Variáveis locais (não versionar - use .env.example como modelo)
└── .gitignore
```

## Módulos implementados

- **Users** — Autenticação JWT, roles (COMPRADOR, ORGANIZADOR, STAFF), signup/login
- **Events** — CRUD organizador, publicação, catálogo público paginado, detalhes
- **Ticket Lots** — Criação, listagem, ocupação, status (ACTIVE/INACTIVE)
- **Orders** — Reserva, checkout com cartão simulado, expiração, cancelamento
- **Tickets** — Emissão, validação, check-in por staff
- **Event Staff** — Associação organizador↔staff, listagem eventos designados

## Perfis e permissões

| Perfil | Permissões principais |
|--------|----------------------|
| **COMPRADOR** | Navega catálogo público, vê detalhes, reserva/compra ingressos, vê seus tickets/pedidos |
| **ORGANIZADOR** | Cria/edita eventos, gerencia lotes, publica eventos, vê ocupação, gerencia staff do evento |
| **STAFF** | Vê eventos associados, faz check-in de ingressos na portaria |

## Desenvolvimento local com Docker Compose

### Pré-requisitos
- Docker 24+ e Docker Compose v2+
- Porta 5432 (PostgreSQL), 8080 (Backend), 4200 (Frontend) livres

### 1. Configure variáveis de ambiente

Copie o modelo e ajuste se necessário:

```bash
cp .env.example .env  # ou crie .env manualmente
```

Conteúdo esperado do `.env` (não versionado):

```env
POSTGRES_DB=eventbook
POSTGRES_USER=eventbook
POSTGRES_PASSWORD=devpass
POSTGRES_PORT=5433          # Altere se 5432 estiver ocupada
CURL_VERSION=8.18.0-1ubuntu2.8
JWT_SECRET=seu-seguro-jwt-aqui  # Mínimo 32 chars, gere com: openssl rand -base64 32
```

> **Segurança:** O arquivo `.env` contém segredos locais. **Nunca commite** este arquivo. O `.gitignore` já o ignora.

### 2. Suba a stack completa

```bash
docker compose up -d --build
```

Isso inicia três serviços:
- **db** — PostgreSQL 17 em `localhost:5433` (ou `POSTGRES_PORT`)
- **backend** — Spring Boot em `http://localhost:8080`
- **frontend** — Angular dev server em `http://localhost:4200`

### 3. Verifique saúde dos serviços

```bash
docker compose ps
# Todos devem mostrar "healthy" ou "running"

docker compose logs -f backend   # Aguarde "Started BackendApplication"
docker compose logs -f frontend  # Aguarde "Compiled successfully"
```

### 4. Acesse a aplicação

| Serviço | URL | Observação |
|---------|-----|------------|
| Frontend (catálogo público) | http://localhost:4200/shop-events | Sem login necessário |
| Frontend (área logada) | http://localhost:4200/login | Redirect automático para login |
| Backend API (Swagger UI) | http://localhost:8080/swagger-ui.html | Documentação interativa |
| Backend Health | http://localhost:8080/actuator/health | Health check Spring Boot |
| PostgreSQL | `localhost:5433` | User/pass do `.env` |

### 5. Teste rápido end-to-end

1. Abra http://localhost:4200/shop-events → catálogo público carrega (paginado, 12/eventos)
2. Use a barra de pesquisa e filtros (cidade, estado, datas, online/presencial)
3. Clique "Mais Informações" em um evento → detalhes
4. Clique "Comprar" → **Soft Wall**: redireciona para `/login?eventId=...`
5. Faça login (ou cadastre-se) → retorna autenticado ao detalhe do evento com lotes disponíveis
6. Selecione quantidade → "Comprar" → checkout simulado → tickets emitidos

### 6. Banco de dados — Acesso direto

```bash
# Via psql (container)
docker compose exec db psql -U eventbook -d eventbook

# Via pgcli (se instalado localmente)
pgcli -h localhost -p 5433 -U eventbook -d eventbook

# Queries úteis
SELECT * FROM events WHERE status = 'PUBLISHED' LIMIT 5;
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

### 7. Parar e limpar

```bash
docker compose stop           # Para containers, mantém volumes (dados persistem)
docker compose down           # Para e remove containers, redes
docker compose down -v        # **Remove volumes** → apaga dados do PostgreSQL
```

---

## Testes locais

### Backend (Maven)

```bash
cd backend

# Testes unitários + integração (Testcontainers sobe PG temporário)
./mvnw test

# Apenas testes de controller (rápidos, mockam repos)
./mvnw test -Dtest=EventControllerTest

# Verifica estilo (Checkstyle/SpotBugs se configurado)
./mvnw verify
```

### Frontend (npm / Vitest)

```bash
cd frontend/event-book-frontend

# Instala dependências (apenas primeira vez ou se package-lock mudou)
npm ci

# Testes unitários (watch mode)
npm test

# Testes unitários (CI mode — roda uma vez e sai)
npm run test:ci  # ou: npx vitest run

# Build de produção (verifica budget 500kB, otimizações)
npm run build

# Lint / formatação
npm run lint        # se configurado
npm run format      # prettier
```

### E2E (Playwright — opcional)

```bash
cd frontend/event-book-frontend
npx playwright install --with-deps chromium  # primeira vez
npx playwright test                          # roda testes e2e
```

---

## Variáveis de ambiente por ambiente

| Variável | Local (`.env`) | Staging/Produção (Render/Vercel) |
|----------|----------------|----------------------------------|
| `POSTGRES_DB` | eventbook | Definido no Neon |
| `POSTGRES_USER` | eventbook | Definido no Neon |
| `POSTGRES_PASSWORD` | devpass | **Segredo no Neon/Render** |
| `POSTGRES_PORT` | 5433 | 5432 (interno Neon) |
| `JWT_SECRET` | openssl rand -base64 32 | **Segredo no Render** |
| `CURL_VERSION` | 8.18.0-1ubuntu2.8 | Build arg Docker |
| `DB_URL` | `jdbc:postgresql://db:5432/...` | `jdbc:postgresql://<neon-host>/...` |
| `API_URL` (frontend) | `http://localhost:8080/api` | `https://eventbook-backend.onrender.com/api` |

> **Nunca** commite `.env`, `.env.local`, ou arquivos com segredos reais. Use os painéis de segredos do Render/Vercel/GitHub Actions.

---

## Documentação técnica

- `docs/architecture/phase-1/01-current-state.md` — Estado atual validado (stack, domínio, contratos)
- `docs/architecture/phase-1/02-location-model.md` — Modelo endereço estruturado + migration V10
- `docs/architecture/phase-1/03-public-search-api.md` — Contrato HTTP catálogo público (filtros, paginação, ordenação)
- `docs/architecture/phase-1/04-database-and-performance.md` — Migration V10 DDL, índices parciais, JPQL, pg_trgm Fase 2
- `docs/architecture/phase-1/05-frontend-specification.md` — Componentes Angular, signals, URL sync, UX, a11y
- `docs/architecture/phase-1/06-security-and-testing.md` — Segurança (Soft Wall, LGPD, validação), planos de teste
- `docs/architecture/phase-1/07-implementation-plan.md` — 32 tarefas, dependências, critérios de aceite, cronograma 4 semanas
- `docs/architecture/phase-1/08-final-report.md` — Relatório consolidado da auditoria
- `docs/architecture/phase-1/review/` — Revisão de consistência, matriz de decisões, dependências da equipe

---

## Convenções

- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/pt-br/) em português
  - `feat: adiciona filtro por cidade no catálogo público`
  - `fix: corrige ordenação determinística por id tie-breaker`
  - `docs: atualiza README com instruções de teste local`
- **Branches:** `feature/...`, `fix/...`, `docs/...`, `chore/...`
- **PRs:** Requerem aprovação de pelo menos 1 revisor (cross-role quando possível)
- **Code style:** Checkstyle (backend), Prettier + ESLint (frontend)

---

## Status atual

**MVP funcional** — Catálogo público com paginação, autenticação JWT, Soft Wall, CRUD organizador, lotes, checkout simulado, emissão de tickets, check-in staff.

**Próxima evolução (Fase 1 planejada):** Endereço estruturado (venue, rua, número, bairro, cidade, estado, CEP, coords, online), pesquisa textual, filtros por localização/data, paginação integrada, sincronização URL.

Consulte `docs/architecture/phase-1/` para a especificação técnica completa e `docs/architecture/phase-1/review/` para decisões pendentes de aprovação.

---

## Licença

MIT — veja [LICENSE](LICENSE).