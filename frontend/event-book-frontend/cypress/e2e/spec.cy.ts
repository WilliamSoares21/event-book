
describe('Login - Event Book', () => {

  beforeEach(() => {
    cy.visit('/');
  });

  it('deve redirecionar para a página de login', () => {
    cy.location('pathname').should('eq', '/login');
  });

  it('deve exibir os elementos do formulário', () => {
    cy.contains('h1', 'Bem-vindo de volta!').should('be.visible');

    cy.get('#email').should('be.visible');
    cy.get('#password').should('be.visible');

    cy.get('button[type="submit"]')
      .should('be.visible')
      .and('contain', 'Entrar');
  });

  it('deve apresentar mensagem ao enviar o formulário vazio', () => {
    cy.get('button[type="submit"]').click();

    cy.contains(
      'Informe um e-mail válido e uma senha com pelo menos 8 caracteres.'
    ).should('be.visible');
  });

  it('deve realizar login com sucesso', () => {
    cy.intercept('POST', '**/auth/login', {
      statusCode: 200,
      body: {
        token: 'token-teste-123',
        user: {
          id: 1,
          name: 'Leonardo',
          email: 'leonardo@teste.com',
          role: 'ORGANIZADOR',
          createdAt: '2026-11-19T10:00:00Z'
        }
      }
    }).as('loginSucesso');

    cy.get('#email').type('leonardo@teste.com');
    cy.get('#password').type('senha12345');

    cy.get('button[type="submit"]').click();

    cy.wait('@loginSucesso').then(({ request }) => {
      expect(request.body.email).to.equal('leonardo@teste.com');
      expect(request.body.password).to.equal('senha12345');
    });

    cy.location('pathname', { timeout: 10000 })
      .should('eq', '/events');

    cy.window().then((window) => {
      const session = JSON.parse(
        window.sessionStorage.getItem('event-book-user')!
      );

      expect(session.email).to.equal('leonardo@teste.com');
      expect(session.role).to.equal('ORGANIZADOR');
      expect(session.token).to.equal('token-teste-123');
    });
  });

});
