import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { SessionService } from '../../../services/session/session';
import { LoginComponent } from '../../login/login';

describe('StaffEvents', () => {
  it('entra como staff, carrega seus eventos e mostra somente a opção Staff Eventos', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const session = TestBed.inject(SessionService);
    session.clear();
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    const login = await harness.navigateByUrl('/login', LoginComponent);

    login.loginForm.setValue({ email: 'staff@example.com', password: '12345678' });
    login.onSubmit();
    http.expectOne((request) => request.url.endsWith('/auth/login')).flush({
      token: 'staff-token',
      user: { id: 'staff-1', name: 'Staff', email: 'staff@example.com', role: 'STAFF' },
    });
    await harness.fixture.whenStable();

    const request = http.expectOne((entry) => entry.url.endsWith('/api/staff/events'));
    expect(request.request.headers.get('Authorization')).toBe('Bearer staff-token');
    request.flush([
      { id: 'event-1', title: 'Evento vinculado', startsAt: '2027-06-15T18:30:00', location: 'Fortaleza' },
    ]);
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/staff/events');
    const page = harness.routeNativeElement!;
    expect(page.querySelector('article')?.textContent).toContain('Evento vinculado');
    expect(Array.from(page.querySelectorAll('nav a'), (link) => link.getAttribute('href')))
      .toEqual(['/staff/events']);
    http.verify();
    session.clear();
  });
});
