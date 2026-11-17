import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly notification = signal<{ message: string; success: boolean } | null>(null);

  show(message: string, success = false) {
    this.notification.set({ message, success });
  }

  dismiss() {
    this.notification.set(null);
  }
}
