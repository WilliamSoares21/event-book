import { Component } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { LoginService } from '../../services/login/login';
import { LoginRequest } from '../../model/LoginRequest';
import { response } from 'express';

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

  constructor(
    private route: ActivatedRoute,
    private fb: FormBuilder,
    private loginService: LoginService
  ) {
    this.singUpForm = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      this.perfilSelecionado = params.get('perfil') ?? '';
    });
  }

  onSubmit() {
    if (this.singUpForm.valid) {
      const requestLogin: LoginRequest = {
        name: this.singUpForm.value.name,
        email: this.singUpForm.value.email,
        password: this.singUpForm.value.password
      }

      this.loginService.register(requestLogin).subscribe({
        next: (response) => {

        },
        error: (error) => {
          console.error('', error)
        }
      });
    }
  }
}