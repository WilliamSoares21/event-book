import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { AcessoGuard } from './AcessoGuard';
import { MockUser, SessionService } from '../services/session/session';

describe('AcessoGuard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  afterEach(() => sessionStorage.clear());

  const access = (allowedRoles?: string[]) => TestBed.runInInjectionContext(() =>
    AcessoGuard({ data: { allowedRoles } } as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  for (const role of ['ORGANIZADOR', 'COMPRADOR'] as const) {
    it(`mantém o acesso de ${role} sem depender do estado da navegação`, () => {
      TestBed.inject(SessionService).login({ id: '123', role, name: 'Fulano', email: 'fulano@gmail.com' });
      expect(access()).toBe(true);
      expect(access()).toBe(true);
    });
  }

  it('restaura a sessão ao recarregar', () => {
    const user: MockUser = { id: '123', role: 'ORGANIZADOR', name: 'Fulano', email: 'fulano1@gmail.com' };
    sessionStorage.setItem('event-book-user', JSON.stringify(user));
    expect(TestBed.inject(SessionService).user()).toEqual(user);
    expect(access()).toBe(true);
  });

  it('restaura a sessão de STAFF e limita o acesso às rotas desse perfil', () => {
    const user: MockUser = { id: '123', role: 'STAFF', name: 'Staff', email: 'staff@example.com' };
    sessionStorage.setItem('event-book-user', JSON.stringify(user));
    expect(TestBed.inject(SessionService).user()).toEqual(user);
    expect(access(['STAFF'])).toBe(true);
    expect(access()).not.toBe(true);
  });

  it('redireciona usuários sem sessão para login', () => {
    const result = access();
    expect(TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>))
      .toContain('/login?mensagem=');
  });
});
