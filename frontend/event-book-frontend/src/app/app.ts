import { Component, inject, signal } from '@angular/core';
import { ToastService } from './services/toast/toast';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly toast = inject(ToastService);
  protected readonly title = signal('event-book-frontend');
}
