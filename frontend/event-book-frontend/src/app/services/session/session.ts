import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

export interface MockUser {
  id: string;
  role: 'ORGANIZADOR' | 'COMPRADOR' | 'VISITANTE';
  name: string;
  email: string;
}

// Sessão apenas para o fluxo mockado; autorização real deve vir do backend.
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  readonly user = signal<MockUser | null>(this.restore());

  login(user: MockUser) {
    this.user.set(user);
    if (this.browser) {
      try { sessionStorage.setItem('event-book-user', JSON.stringify(user)); } catch {}
    }
  }

  private restore(): MockUser | null {
    if (!this.browser) return null;
    try {
      const user = JSON.parse(sessionStorage.getItem('event-book-user') ?? 'null');
      return user && typeof user.id === 'string' && typeof user.email === 'string'
        && typeof user.name === 'string'
        && ['ORGANIZADOR', 'COMPRADOR', 'VISITANTE'].includes(user.role) ? user : null;
    } catch {
      return null;
    }
  }
}
