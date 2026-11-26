import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { LoginComponent } from '../../login/login';
import { SessionService } from '../../../services/session/session';

describe('ShopEvents', () => {
  it('returns to the selected event after login from the public catalog', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(SessionService).clear();
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create('/shop-events');
    const event = {
      id: 'event-1',
      title: 'Encontro de tecnologia',
      startsAt: '2027-06-15T18:30:00',
      location: 'Centro de convenções',
    };

    http.expectOne((req) => req.url.endsWith('/api/events/available')).flush({
      content: [event],
      page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true
    });
    harness.detectChanges();

    const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('article a')!;
    expect(link.textContent).toContain('Mais Informações');
    expect(link.getAttribute('href')).toBe('/shop-events/event-1');
    await harness.navigateByUrl(link.getAttribute('href')!);

    const detail = http.expectOne((req) => req.url.endsWith('/api/events/event-1'));
    expect(detail.request.headers.has('Authorization')).toBe(false);
    const publishedEvent = {
      ...event,
      endsAt: '2027-06-15T21:30:00',
      description: 'Palestras e oficinas',
      status: 'PUBLISHED',
    };
    detail.flush(publishedEvent);
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/shop-events/event-1');
    expect(harness.routeNativeElement?.textContent).toContain('Palestras e oficinas');
    expect(harness.routeNativeElement?.querySelector('app-menu-side-bar')).toBeNull();
    expect(harness.routeNativeElement?.querySelector('.back-link')?.getAttribute('href'))
      .toBe('/shop-events');
    const buyLink = Array.from(harness.routeNativeElement!.querySelectorAll('a'))
      .find((anchor) => anchor.textContent?.trim() === 'Comprar')!;
    expect(buyLink.getAttribute('href')).toBe('/login?eventId=event-1');

    const login = await harness.navigateByUrl(buyLink.getAttribute('href')!, LoginComponent);
    login.loginForm.setValue({ email: 'buyer@example.com', password: '12345678' });
    login.onSubmit();
    http.expectOne((req) => req.url.endsWith('/auth/login')).flush({
      token: 'buyer-token',
      user: { id: 'buyer', name: 'Comprador', email: 'buyer@example.com', role: 'COMPRADOR' },
    });
    await harness.fixture.whenStable();

    const authenticatedDetail = http.expectOne((req) => req.url.endsWith('/api/events/event-1'));
    expect(authenticatedDetail.request.headers.get('Authorization')).toBe('Bearer buyer-token');
    authenticatedDetail.flush({
      ...publishedEvent,
      availableTicketLots: [{ id: 'lot-1', name: 'Lote inicial', unitPrice: 50, availableQuantity: 2 }],
    });
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/events/event-1');
    expect(harness.routeNativeElement?.querySelector('main button[type="submit"]')?.textContent)
      .toContain('Comprar');
    http.verify();
    TestBed.inject(SessionService).clear();
  });

  it('debounce a busca e envia apenas uma requisição por pausa', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create('/shop-events');

    http.expectOne((req) => req.url.endsWith('/api/events/available')).flush({
      content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true
    });
    harness.detectChanges();

    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#search-name')!;
    input.value = 'te';
    input.dispatchEvent(new Event('input'));
    input.value = 'tech';
    input.dispatchEvent(new Event('input'));

    await new Promise(resolve => setTimeout(resolve, 350));

    const searches = http.match((req) => req.url.endsWith('/api/events/available'));
    expect(searches).toHaveLength(1);
    expect(searches[0].request.params.get('q')).toBe('tech');
    searches[0].flush({
      content: [{ id: 'event-2', title: 'Conferência Tech', startsAt: '2027-06-15T18:30:00', location: 'Fortaleza' }],
      page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true
    });
    harness.detectChanges();

    expect(harness.routeNativeElement!.textContent).toContain('Conferência Tech');
    http.verify();
  });
});
