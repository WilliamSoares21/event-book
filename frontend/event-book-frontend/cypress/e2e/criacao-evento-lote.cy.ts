
describe('Criação de evento e lote - Event Book', () => {

  const evento = {
    title: 'Evento E2E Cypress',
    description: 'Evento criado automaticamente pelo teste E2E.',
    location: 'Centro de Eventos - Fortaleza'
  };

  const lote = {
    name: 'Lote Promocional E2E',
    unitPrice: 50,
    capacity: 100
  };

  beforeEach(() => {
    cy.visit('/login');

    cy.window().then((window) => {
      window.sessionStorage.setItem(
        'event-book-user',
        JSON.stringify({
          token: 'token-teste-123',
          id: '1',
          role: 'ORGANIZADOR',
          name: 'Leonardo',
          email: 'leonardo@teste.com'
        })
      );
    });
  });

  it('deve criar um evento e seu lote com sucesso', () => {

    cy.intercept('POST', '**/api/events', {
      statusCode: 201,
      body: {
        id: '100',
        ...evento,
        startsAt: '2027-12-15T18:00:00.000Z',
        endsAt: '2027-12-15T22:00:00.000Z'
      }
    }).as('criarEvento');

    cy.intercept('POST', '**/api/events/100/ticket-lots', {
      statusCode: 201,
      body: {
        id: '200',
        ...lote,
        salesStart: '2027-11-01T00:00:00.000Z',
        salesEnd: '2027-12-14T00:00:00.000Z'
      }
    }).as('criarLote');

    cy.visit('/events/create');

    // Preenchimento dos dados do evento
    cy.get('#eventName').type(evento.title);
    cy.get('#eventDescription').type(evento.description);
    cy.get('#eventStartedDate').type('2027-12-15');
    cy.get('#eventStartedTime').type('15:00');
    cy.get('#eventEndDate').type('2027-12-15');
    cy.get('#eventEndTime').type('19:00');
    cy.get('#eventLocation').type(evento.location);

    cy.contains('button', 'Próximo').click();

    cy.location('pathname')
      .should('eq', '/events/lots/create');

    // Preenchimento do primeiro lote
    cy.get('#nome-lote-1').type(lote.name);
    cy.get('#valor-ingresso-1').type(String(lote.unitPrice));
    cy.get('#qtd-ingresso-1').type(String(lote.capacity));
    cy.get('#data-inicio-vendas-1').type('2027-11-01');
    cy.get('#data-fim-vendas-1').type('2027-12-14');

    cy.contains('button', 'Criar evento').click();

    // Validação da criação do evento
    cy.wait('@criarEvento').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);

      expect(request.headers.authorization)
        .to.equal('Bearer token-teste-123');

      expect(request.body.title).to.equal(evento.title);
      expect(request.body.description).to.equal(evento.description);
      expect(request.body.location).to.equal(evento.location);
    });

    // Validação da criação do lote
    cy.wait('@criarLote').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);

      expect(request.url).to.include('/events/100/ticket-lots');

      expect(request.headers.authorization)
        .to.equal('Bearer token-teste-123');

      expect(request.body.name).to.equal(lote.name);
      expect(request.body.unitPrice).to.equal(lote.unitPrice);
      expect(request.body.capacity).to.equal(lote.capacity);
    });

    // Validação da mensagem de sucesso
    cy.contains(
      'Evento e lotes criados com sucesso!',
      { timeout: 10000 }
    ).should('be.visible');


    // Validação do redirecionamento
    cy.location('pathname', { timeout: 10000 })
      .should('eq', '/events');

  });

});
