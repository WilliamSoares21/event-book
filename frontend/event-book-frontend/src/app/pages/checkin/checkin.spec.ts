import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { SessionService } from '../../services/session/session';

describe('Checkin', () => {
  it('abre pelo card, registra o ingresso e delega a validação de duplicidade à API', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: { user: signal({ role: 'STAFF', token: 'staff-token' }) } },
      ],
    });

    const event = { id: 'event-1', title: 'Evento da equipe', startsAt: '2027-06-15T18:30:00', location: 'Fortaleza' };
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create('/staff/events');
    http.expectOne((request) => request.url.endsWith('/api/staff/events')).flush([event]);
    harness.detectChanges();

    const link = harness.routeNativeElement!.querySelector('article a')!;
    expect(link.getAttribute('href')).toBe('/staff/events/event-1/check-in');
    await harness.navigateByUrl(link.getAttribute('href')!);
    http.expectOne((request) => request.url.endsWith('/api/staff/events')).flush([event]);
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    expect(page.querySelector('h2')?.textContent).toBe(event.title);
    expect(page.querySelector('time')?.getAttribute('datetime')).toBe(event.startsAt);
    const input = page.querySelector<HTMLInputElement>('#ticket-code')!;
    const button = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    const request = () => http.expectOne((entry) =>
      entry.method === 'POST' && entry.url.endsWith('/api/tickets/ABC123/check-in'));
    const response = { ticketCode: 'ABC123', checkedInAt: '2027-06-15T18:00:00Z', staffId: 'staff-1' };

    input.value = ' ABC123 ';
    button.click();
    const first = request();
    expect(first.request.headers.get('Authorization')).toBe('Bearer staff-token');
    first.flush(response);
    harness.detectChanges();
    expect(page.querySelector('[role="status"]')?.textContent).toContain('Check-in realizado');
    expect(input.value).toBe('');

    input.value = 'ABC123';
    button.click();
    request().flush(response, { status: 409, statusText: 'Conflict' });
    harness.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('já possui check-in');
    expect(button.disabled).toBe(false);
    http.verify();
  });
});
