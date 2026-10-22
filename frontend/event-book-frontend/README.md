# EventBook Frontend

Frontend do projeto EventBook, desenvolvido em Angular para apoiar o fluxo de autenticação, cadastro e onboarding de usuários.

## 🚀 Tecnologias

- Angular 22
- TypeScript
- Bootstrap
- RxJS
- Angular SSR
- npm

## 📋 Pré-requisitos

Antes de iniciar, certifique-se de ter instalado:

- Node.js 20 ou superior
- npm 10 ou superior

## 🔧 Instalação

Clone o repositório e instale as dependências:

```bash
git clone <url-do-repositorio>
cd event-book-frontend
npm install
```

## ▶️ Executando o projeto

Para iniciar o ambiente de desenvolvimento:

```bash
npm start
```

ou:

```bash
ng serve
```

A aplicação ficará disponível em:

```text
http://localhost:4200
```

## 🏗️ Build de produção

Para gerar a build de produção:

```bash
npm run build
```

Os arquivos compilados serão gerados na pasta `dist/`.

## ✅ Testes

Para executar os testes unitários:

```bash
npm test
```

## 📁 Estrutura principal

```text
src/
  app/
    components/
    pages/
      login/
      singup/
      singup-perfil-type/
    services/
  styles.css
```

## 🧩 Observações

- O projeto utiliza a arquitetura de componentes do Angular.
- A estrutura atual já contempla páginas de login e cadastro.
- O projeto pode evoluir com integração com backend e autenticação real.

## 👨‍💻 Fluxo de desenvolvimento

1. Crie uma branch para a funcionalidade ou correção.
2. Faça as alterações necessárias.
3. Execute os testes e valide a aplicação.
4. Abra um pull request com a descrição da mudança.

## 📚 Recursos adicionais

- [Documentação do Angular](https://angular.dev/)
- [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli)
