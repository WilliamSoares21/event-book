# Documentação técnica

Esta pasta reúne referências curtas e documentos que precisam acompanhar o código. A documentação detalhada de arquitetura, stack, módulos e regras de negócio está na Wiki do GitHub:

- [Arquitetura Inicial do EventBook](https://github.com/WilliamSoares21/event-book/wiki/Arquitetura-Inicial-Event-Book)
- [Página inicial da Wiki](https://github.com/WilliamSoares21/event-book/wiki)

## Estrutura do monorepo

```text
event-book/
├── .github/workflows/  # CI e automações
├── backend/            # API Spring Boot
├── frontend/           # Aplicação Angular
├── infra/              # Infraestrutura e execução local
└── docs/               # Documentação técnica
```

As pastas `backend/` e `frontend/` são responsabilidade dos respectivos desenvolvedores. Esta branch estrutura os pontos compartilhados do monorepo sem recriar esses diretórios.
