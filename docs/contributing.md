# Convenções de contribuição

## Branches

As branches devem indicar o tipo da mudança e usar nomes em minúsculas, separados por hífens:

```text
feature/nome-da-funcionalidade
fix/nome-do-problema
chore/nome-da-tarefa
docs/nome-da-documentacao
```

Exemplo: `feature/criar-check-in`.

Cada branch deve tratar de um objetivo coeso. A branch `main` recebe somente mudanças revisadas por pull request. Antes de abrir um pull request, atualize sua branch com a `main` e verifique os testes e validações pertinentes ao módulo alterado.

## Pull requests

Descreva o objetivo da mudança, os diretórios afetados e como ela foi validada. Solicite a revisão dos responsáveis pelos módulos envolvidos e mantenha a documentação atualizada quando a arquitetura ou o fluxo de desenvolvimento mudar.

## Organização por responsabilidade

- `backend/`: API e regras de negócio em Spring Boot.
- `frontend/`: interface Angular.
- `infra/`: Docker, ambientes e automações de infraestrutura.
- `docs/`: documentação técnica e referências da Wiki.
