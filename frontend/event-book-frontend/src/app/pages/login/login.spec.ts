import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { LoginComponent } from './login';
import { SessionService } from '../../services/session/session';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    sessionStorage.clear();
  });

  it('salva a sessão do comprador e navega para eventos sem guardar o token no histórico', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.loginForm.setValue({ email: 'fulano1@gmail.com', password: '12345678' });
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    TestBed.inject(HttpTestingController).expectOne((req) => req.url.endsWith('/auth/login')).flush({
      token: 'test-token',
      user: { id: 'buyer', name: 'Fulano', email: 'fulano1@gmail.com', role: 'COMPRADOR' },
    });
    expect(TestBed.inject(SessionService).user()).toMatchObject({ role: 'COMPRADOR', token: 'test-token' });
    expect(navigate).toHaveBeenCalledExactlyOnceWith(['/events']);
  });

  it('exibe orientação e impede navegação com senha curta', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    component.loginForm.setValue({ email: 'fulano1@gmail.com', password: '123' });
    component.onSubmit();
    expect(navigate).not.toHaveBeenCalled();
    expect(component.mensagem()).toContain('8 caracteres');
  });
});
