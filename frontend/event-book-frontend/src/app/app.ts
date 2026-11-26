import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar';
import { FooterComponent } from './components/footer/footer';
import { ToastService } from './services/toast/toast';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent],
  template: `
    <div class="page-wrapper">
      <app-navbar></app-navbar>
      <main class="flex-grow-1">
        <router-outlet></router-outlet>
      </main>
      <app-footer></app-footer>
    </div>
    @if (toast.notification(); as notification) {
      <div class="notification-toast" [class.notification-success]="notification.success"
        [class.notification-error]="!notification.success"
        [attr.role]="notification.success ? 'status' : 'alert'" aria-atomic="true">
        <span>{{ notification.message }}</span>
        <button type="button" (click)="toast.dismiss()" aria-label="Fechar notificação">×</button>
      </div>
    }
  `,
  styleUrls: ['./app.css']
})
export class AppComponent {
  readonly toast = inject(ToastService);
}
