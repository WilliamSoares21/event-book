import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { SessionService } from '../../../services/session/session';

describe('StaffColaborador', () => {
  it('lista colaboradores e associa o selecionado ao evento da rota', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: { user: signal({ id: 'owner', role: 'ORGANIZADOR', token: 'token' }) } },
      ],
    });
    const harness = await RouterTestingHarness.create('/events/event-1');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((request) => request.url.endsWith('/api/events/event-1')).flush({
      id: 'event-1',
      organizerId: 'owner',
      title: 'Evento de teste',
      status: 'PUBLISHED',
    });
    harness.detectChanges();
    const staffLink = harness.routeNativeElement!.querySelector('a[href="/events/event-1/staff"]')!;
    expect(staffLink.textContent).toContain('Adicionar Staff');
    await harness.navigateByUrl(staffLink.getAttribute('href')!);

    const list = http.expectOne((request) =>
      request.method === 'GET' && request.url.endsWith('/api/events/event-1/staff'));
    expect(list.request.headers.get('Authorization')).toBe('Bearer token');
    list.flush([
      { id: 'staff-1', name: 'Ana', associated: false },
      { id: 'staff-2', name: 'Bruno', associated: true },
    ]);
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    expect(page.textContent).toContain('Bruno');
    expect(page.querySelectorAll('li button')).toHaveLength(1);
    page.querySelector<HTMLButtonElement>('button[aria-label="Associar Ana"]')!.click();
    const add = http.expectOne((request) =>
      request.method === 'POST' && request.url.endsWith('/api/events/event-1/staff'));
    expect(add.request.body).toEqual({ userId: 'staff-1' });
    add.flush({}, { status: 201, statusText: 'Created' });
    harness.detectChanges();

    expect(page.querySelectorAll('li button')).toHaveLength(0);
    expect(page.textContent).toContain('Colaborador associado ao evento.');
    http.verify();
  });
});
