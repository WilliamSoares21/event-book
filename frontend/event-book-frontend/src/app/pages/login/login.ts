import { isPlatformBrowser } from '@angular/common';
import { SessionService } from '../../services/session/session';
import { Component, PLATFORM_ID, inject, signal, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LoginService, LoginError } from '../../services/login/login';
import { LoginRequest } from '../../model/LoginRequest';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class LoginComponent implements OnDestroy {
  private readonly session = inject(SessionService);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  mensagem = signal('');
  private timerMensagem?: ReturnType<typeof setTimeout>;
  loginForm: FormGroup;
  private timerErro?: ReturnType<typeof setTimeout>;
  mensagemErro = signal('');
  submitting = signal(false);

  constructor(private route: ActivatedRoute,
     private router: Router,
     private loginService: LoginService) {

    this.loginForm = new FormGroup({
      email: new FormControl('', [Validators.required, Validators.email]),
      password: new FormControl('', [Validators.required, Validators.minLength(8)])
    });
  }
  
   ngOnInit() {
      const { mensagem, ...restante } = (this.browser ? history.state : null) ?? {};
      this.mensagem.set(mensagem ?? this.route.snapshot.queryParamMap.get('mensagem') ?? '');
      if (this.browser) history.replaceState(restante, '');
      if (this.mensagem()) {
        this.timerMensagem = setTimeout(() => this.mensagem.set(''), 4000);
      }
    }

  ngOnDestroy() {
    clearTimeout(this.timerMensagem);
    clearTimeout(this.timerErro);
  }

  onSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.mensagem.set('Informe um e-mail válido e uma senha com pelo menos 8 caracteres.');
      return;
    }

    this.submitting.set(true);
    this.mensagemErro.set('');

    const email = this.loginForm.value.email.trim().toLowerCase();
    const requestLogin: LoginRequest ={ name: null, email, password: this.loginForm.value.password, role: null };
    const eventId = this.route.snapshot.queryParamMap.get('eventId')?.trim();
    this.loginService.login(requestLogin)
    .subscribe({
      next: (response) => {
        this.submitting.set(false);
        const role = response.user.role.toUpperCase();
        this.session.login({
          token: response.token,
          id: response.user.id.toString(),
          role,
          name: response.user.name,
          email: response.user.email,
        });
        if (role === 'STAFF') {
          this.router.navigate(['/staff/events']);
        } else {
          this.router.navigate(eventId ? ['/events', eventId] : ['/events']);
        }
      },
      error: (error: LoginError) => {
        this.submitting.set(false);
        this.exibirErro(error.message);
      }
    });
  }

  private exibirErro(mensagem: string) {
    clearTimeout(this.timerErro);
    this.mensagemErro.set(mensagem);
    this.timerErro = setTimeout(() => {
      this.mensagemErro.set('');
    }, 5000);
  }
}
