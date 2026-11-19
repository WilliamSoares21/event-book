import { Component, inject, Input, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LoginService } from '../../services/login/login';
import { SessionService } from '../../services/session/session';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-menu-side-bar',
  styleUrl: './menu-side-bar.css',
  templateUrl: './menu-side-bar.html',
})
export class MenuSideBarComponent {
  @Input() roleUser: string | undefined;

  private readonly loginService = inject(LoginService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  readonly loggingOut = signal(false);

  logout() {
    if (this.loggingOut()) return;

    const user = this.session.user();
    const finish = (message?: string) => {
      this.loggingOut.set(false);
      if (this.session.user() !== user) return;

      this.session.clear();
      void this.router.navigate(['/login'], {
        replaceUrl: true,
        state: message ? { mensagem: message } : undefined,
      });
    };

    if (!user?.token) {
      finish();
      return;
    }

    this.loggingOut.set(true);
    this.loginService.logout(user.token).subscribe({
      next: () => finish(),
      error: (error) => finish(error.status === 401
        ? undefined
        : 'Sessão local encerrada. O servidor não confirmou o logout.'),
    });
  }
}
