import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LoginService } from '../../services/login/login';
import { LoginRequest } from '../../model/LoginRequest';

@Component({
  selector: 'app-singup',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './singup.html',
  styleUrl: './singup.css',
})
export class SingupComponent {
  singUpForm: FormGroup;
  perfilSelecionado!: string;
  mensagemErro = signal('');
  private timerErro?: ReturnType<typeof setTimeout>;

  constructor(
    private route: ActivatedRoute,
    private fb: FormBuilder,
    private loginService: LoginService,
    private router: Router
  ) {
    this.singUpForm = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      this.perfilSelecionado = params.get('perfil') ?? '';
    });
  }

  onSubmit() {
    if (this.singUpForm.valid) {
      clearTimeout(this.timerErro);
      this.mensagemErro.set('');
      const requestLogin: LoginRequest = {
        name: this.singUpForm.value.name,
        email: this.singUpForm.value.email,
        password: this.singUpForm.value.password,
        role: this.perfilSelecionado.toLocaleUpperCase()  
      }

      this.loginService.register(requestLogin).subscribe({
        next: (response) => {
          this.router.navigate(['/login'], { state: { mensagem: 'Cadastro realizado com sucesso!' } });
        },
        error: (error) => {
          const body = error.error;
          const mensagemErro = body?.fields?.password ??
            body?.fields?.email ??
            body?.fields?.name ??
            body?.error ??
            'Entre em contato com o nosso suporte para mais informações.';

          this.exibirErro('Não foi possível realizar o cadastro. ' + mensagemErro + '.');    
        }
      });
  }
}

  private exibirErro(mensagem: string) {
    clearTimeout(this.timerErro);
    this.mensagemErro.set(mensagem);
    this.timerErro = setTimeout(() => {
      this.mensagemErro.set('');
    }, 4000);
  }

  ngOnDestroy() {
    clearTimeout(this.timerErro);
  }
}
