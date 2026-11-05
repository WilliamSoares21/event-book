import { isPlatformBrowser } from '@angular/common';
import { SessionService } from '../../services/session/session';
import { Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

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

  constructor(private route: ActivatedRoute, private router: Router) {
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
    const organizador = email === 'fulano1@gmail.com';
    const comprador = email === 'fulano2@gmail.com';
    this.session.login({
      id: 'd41d8cd98f00b204e9800998ecf8427e',
      role: organizador ? 'ORGANIZADOR' : comprador ? 'COMPRADOR' : 'VISITANTE',
      name: organizador ? 'Fulano 1' : comprador ? 'Fulano 2' : 'Visitante',
      email,
    });
    this.router.navigate(['/events']);
  }
}
