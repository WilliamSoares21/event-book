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
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.clear());

  it('autentica o vendedor mockado e navega uma vez ao enviar o formulário', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.loginForm.setValue({ email: 'fulano1@gmail.com', password: '12345678' });
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    expect(TestBed.inject(SessionService).user()?.role).toBe('VENDEDOR');
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
