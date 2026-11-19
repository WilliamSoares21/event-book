import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { SessionService } from '../../../services/session/session';
import { TicketsComponent } from '../tickets';
import { DetailsTickets } from './details-tickets';

describe('DetailsTickets', () => {
  let http: HttpTestingController;
  const ticket = {
    id: 'ticket-2',
    eventTitle: 'Evento selecionado',
    eventStartsAt: '2027-06-15T18:30:00',
    uniqueCode: 'CHECKIN-2',
    status: 'VALID',
  };
  const tickets = [{ ...ticket, id: 'ticket-1', eventTitle: 'Outro evento', uniqueCode: 'CHECKIN-1' }, ticket];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'tickets', component: TicketsComponent },
          { path: 'tickets/:id', component: DetailsTickets },
        ]),
        { provide: SessionService, useValue: { user: () => ({ role: 'COMPRADOR', token: 'test-token' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the selected ticket and its check-in code', async () => {
    const harness = await RouterTestingHarness.create('/tickets');
    http.expectOne((request) => request.url.endsWith('/api/tickets/me')).flush(tickets);
    harness.detectChanges();

    const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href="/tickets/ticket-2"]')!;
    expect(link.textContent).toContain('Mais Informações');
    const component = await harness.navigateByUrl(link.getAttribute('href')!, DetailsTickets);

    const request = http.expectOne((request) => request.url.endsWith('/api/tickets/me'));
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush(tickets);
    harness.detectChanges();
    expect(await component.qrImage()).toMatch(/^data:image\/svg\+xml,/);
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    expect(page.querySelector('.card-title')?.textContent).toBe(ticket.eventTitle);
    expect(page.querySelector('.ticket-code')?.textContent).toBe(ticket.uniqueCode);
    expect(page.querySelector('.ticket-qr')?.getAttribute('src')).toMatch(/^data:image\/svg\+xml,/);
  });

  it('shows no ticket when the route ID is absent from the buyer’s list', async () => {
    const harness = await RouterTestingHarness.create('/tickets/unknown');
    http.expectOne((request) => request.url.endsWith('/api/tickets/me')).flush(tickets);
    harness.detectChanges();

    expect(harness.routeNativeElement?.querySelector('[role="alert"]')?.textContent)
      .toContain('Ticket não encontrado.');
    expect(harness.routeNativeElement?.querySelector('.ticket-code')).toBeNull();
  });
});
