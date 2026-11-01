import { Component, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  imports: [],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class LoginComponent {
  mensagem = signal('');
  private timerMensagem?: ReturnType<typeof setTimeout>;
  
  constructor(private route: ActivatedRoute) {}
  
   ngOnInit() {
      const { mensagem, ...restante } = history.state ?? {};
      this.mensagem.set(mensagem ?? this.route.snapshot.queryParamMap.get('mensagem') ?? '');
      history.replaceState(restante, '');
      if (this.mensagem()) {
        this.timerMensagem = setTimeout(() => this.mensagem.set(''), 4000);
      }
    }

  ngOnDestroy() {
    clearTimeout(this.timerMensagem);
  }
}
