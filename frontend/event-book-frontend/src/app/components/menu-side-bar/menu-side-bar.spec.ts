import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SessionService } from '../../services/session/session';
import { MenuSideBarComponent } from './menu-side-bar';

@Component({ template: '' })
class LoginPage {}

describe('MenuSideBarComponent logout', () => {
  it.each([204, 500])('closes the local session when logout returns %s', async (status) => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: LoginPage }]),
      ],
    });
    const session = TestBed.inject(SessionService);
    session.login({ id: 'buyer', name: 'Buyer', email: 'buyer@example.test', role: 'COMPRADOR', token: 'test-token' });
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(MenuSideBarComponent);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.logout-button') as HTMLButtonElement;
    button.click();
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);

    const request = http.expectOne((req) => req.url.endsWith('/api/auth/logout'));
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush(null, { status, statusText: status === 204 ? 'No Content' : 'Server Error' });
    await fixture.whenStable();

    expect(session.user()).toBeNull();
    expect(sessionStorage.getItem('event-book-user')).toBeNull();
    expect(router.url).toBe('/login');
    http.verify();
  });
});
