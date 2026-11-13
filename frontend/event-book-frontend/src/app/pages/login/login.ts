import { isPlatformBrowser } from '@angular/common';
import { SessionService } from '../../services/session/session';
import { Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LoginService } from '../../services/login/login';
import { LoginRequest } from '../../model/LoginRequest';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class LoginComponent {
  private readonly session = inject(SessionService);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  mensagem = signal('');
  private timerMensagem?: ReturnType<typeof setTimeout>;
  loginForm: FormGroup;
  private timerErro?: ReturnType<typeof setTimeout>;
  mensagemErro: ReturnType<typeof signal> = signal('');

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
  }

  onSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.mensagem.set('Informe um e-mail válido e uma senha com pelo menos 8 caracteres.');
      return;
    }

    const email = this.loginForm.value.email.trim().toLowerCase();
    const requestLogin: LoginRequest ={ name: null, email, password: this.loginForm.value.password, role: null };
    this.loginService.login(requestLogin)
    .subscribe({
      next: (response) => {
          this.session.login({
            token: response.token,
            id: response.user.id.toString(),
            role: response.user.role.toUpperCase(),
            name: response.user.name,
            email: response.user.email,
          });
          this.router.navigate(['/events'], { state: { 
            token: response.token,
            user: {
              "id": response.user.id,
              "name": response.user.name,
              "email": response.user.email,
              "role": response.user.role,
              "createdAt": response.user.createdAt
          } } });
        },
        error: (error) => {
          const body = error.error;
          const mensagemErro = body?.fields?.password ??
            body?.fields?.email ??
            body?.error ??
            'Entre em contato com o nosso suporte para mais informações.';

          this.exibirErro('Não foi possível realizar o cadastro. ' + mensagemErro + '.');    
        }
    });
  }

  private exibirErro(mensagem: string) {
    clearTimeout(this.timerErro);
    this.mensagemErro.set(mensagem);
    this.timerErro = setTimeout(() => {
      this.mensagemErro.set('');
    }, 4000);
  }
}
