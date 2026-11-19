import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { SessionService } from '../../../services/session/session';
import { DetailsEvents } from './details-events';

describe('DetailsEvents', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const user = signal({ id: 'owner', role: 'ORGANIZADOR', token: 'test-token' });
  const event = {
    id: 'event-1',
    organizerId: 'owner',
    title: 'Encontro de tecnologia',
    description: 'Palestras e oficinas',
    startsAt: '2027-06-15T18:30:00',
    endsAt: '2027-06-15T21:30:00',
    location: 'Centro de convenções',
    status: 'DRAFT',
    version: 0,
  };

  beforeEach(async () => {
    user.set({ id: 'owner', role: 'ORGANIZADOR', token: 'test-token' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'events/:id', component: DetailsEvents }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: { user } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create('/events/event-1');
  });

  afterEach(() => http.verify());

  it('loads the draft with authentication and updates the status after publication', () => {
    const detail = http.expectOne((req) => req.method === 'GET' && req.url.endsWith('/api/events/event-1'));
    expect(detail.request.headers.get('Authorization')).toBe('Bearer test-token');
    detail.flush(event);
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    for (const value of [event.title, event.description, event.location, '15/06/2027 18:30', '15/06/2027 21:30']) {
      expect(page.textContent).toContain(value);
    }

    const button = page.querySelector<HTMLButtonElement>('main button')!;
    button.click();
    button.click();
    const publication = http.expectOne((req) =>
      req.method === 'POST' && req.url.endsWith('/api/events/event-1/publication'));
    expect(publication.request.headers.get('Authorization')).toBe('Bearer test-token');
    publication.flush(null, { status: 204, statusText: 'No Content' });
    harness.detectChanges();

    expect(page.querySelector('.custom-etiqueta')?.textContent).toBe('Publicado');
    expect(page.querySelector('main button')).toBeNull();
    expect(page.textContent).toContain('Evento publicado com sucesso.');
  });

  it('shows the backend error and allows another attempt', () => {
    http.expectOne((req) => req.url.endsWith('/api/events/event-1')).flush(event);
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    const button = page.querySelector<HTMLButtonElement>('main button')!;
    button.click();
    http.expectOne((req) => req.url.endsWith('/publication')).flush(
      { error: 'Evento precisa ter ao menos um lote ativo' },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    harness.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent)
      .toContain('Evento precisa ter ao menos um lote ativo');
    expect(page.querySelector('.custom-etiqueta')?.textContent).toBe('Rascunho');
    expect(button.disabled).toBe(false);
  });

  it('only offers publication to the organizer who owns the draft', () => {
    user.set({ id: 'other-organizer', role: 'ORGANIZADOR', token: 'test-token' });
    http.expectOne((req) => req.url.endsWith('/api/events/event-1')).flush(event);
    harness.detectChanges();

    expect(harness.routeNativeElement?.querySelector('main button')).toBeNull();
  });

  it('confirms the requested quantity before reserving tickets from a lot with stock', () => {
    user.set({ id: 'buyer', role: 'COMPRADOR', token: 'test-token' });
    http.expectOne((req) => req.url.endsWith('/api/events/event-1')).flush({
      ...event,
      status: 'PUBLISHED',
      availableTicketLots: [
        { id: 'lot-1', name: 'Primeiro lote', unitPrice: 40, availableQuantity: 1 },
        { id: 'lot-2', name: 'Segundo lote', unitPrice: 50, availableQuantity: 5 },
      ],
    });
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    const lots = page.querySelectorAll('.list-group-item');
    expect(lots).toHaveLength(2);
    expect(lots[0].textContent).toContain('Primeiro lote');
    expect(lots[0].textContent).toMatch(/R\$\s40,00/);
    expect(lots[1].textContent).toMatch(/5\s+ingressos disponíveis/);
    const input = page.querySelector<HTMLInputElement>('#ticket-quantity')!;
    input.value = '2';
    input.dispatchEvent(new Event('input'));
    harness.detectChanges();

    const buy = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    const dialog = page.querySelector('dialog')!;
    // jsdom exposes the element but does not implement the native dialog methods.
    dialog.showModal = () => { dialog.open = true; };
    dialog.close = () => { dialog.open = false; };
    buy.click();
    harness.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('2 ingressos');
    http.expectNone((req) => req.url.endsWith('/api/orders'));

    dialog.querySelector<HTMLButtonElement>('.btn-outline-secondary')!.click();
    expect(dialog.open).toBe(false);
    http.expectNone((req) => req.url.endsWith('/api/orders'));

    buy.click();
    dialog.querySelector<HTMLButtonElement>('.btn-primary')!.click();
    const order = http.expectOne((req) => req.url.endsWith('/api/orders'));
    expect(order.request.method).toBe('POST');
    expect(order.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(order.request.body).toEqual({ ticketLotId: 'lot-2', quantity: 2 });
    order.flush({}, { status: 201, statusText: 'Created' });
    harness.detectChanges();

    expect(page.textContent).toContain('Pedido criado. Pagamento pendente.');
    expect(input.max).toBe('3');
    expect(page.querySelectorAll('.list-group-item')[1].textContent).toMatch(/3\s+ingressos disponíveis/);
  });

  it('hides the purchase form for an organizer even when the event has stock', () => {
    http.expectOne((req) => req.url.endsWith('/api/events/event-1')).flush({
      ...event,
      status: 'PUBLISHED',
      availableTicketLots: [{ id: 'lot-1', name: 'Primeiro lote', unitPrice: 40, availableQuantity: 5 }],
    });
    harness.detectChanges();

    expect(harness.routeNativeElement?.querySelector('#ticket-quantity')).toBeNull();
    expect(harness.routeNativeElement?.querySelector('#available-lots-title')).toBeNull();
  });
});
