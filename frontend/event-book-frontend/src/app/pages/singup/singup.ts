import { Component } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-singup',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './singup.html',
  styleUrl: './singup.css',
})
export class SingupComponent {
  singUpForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private fb: FormBuilder
  ) {
    this.singUpForm = this.fb.group({
      name: ['', Validators.required],
      lastName: ['', Validators.required],
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
      console.log(this.singUpForm.value);
    }
  }

  perfilSelecionado!: string;
}