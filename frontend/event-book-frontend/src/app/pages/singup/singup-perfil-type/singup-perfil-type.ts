import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  imports: [],
  selector: 'app-singup-perfil-type',
  styleUrl: './singup-perfil-type.css',
  templateUrl: './singup-perfil-type.html',
})
export class SingupPerfilTypeComponet {
  perfilSelecionado: string = '';

  constructor(private router: Router) {}

  selecionarPerfil(tipo: string) {
    this.perfilSelecionado = tipo;
    console.log('Perfil selecionado:', tipo);
  }

  continueSingUp() {
    if (this.perfilSelecionado) {
      this.router.navigate(['/singup'], {
        queryParams: { perfil: this.perfilSelecionado }
      });
    }
  }
}
