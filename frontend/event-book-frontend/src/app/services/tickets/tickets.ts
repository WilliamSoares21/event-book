import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { SessionService } from '../session/session';

export interface BuyerTicket {
  id: string;
  eventTitle: string;
  eventStartsAt: string;
  uniqueCode: string;
  status: 'VALID' | 'CHECKED_IN' | 'CANCELLED';
}

export const ticketStatusLabels: Record<BuyerTicket['status'], string> = {
  VALID: 'Válido',
  CHECKED_IN: 'Check-in realizado',
  CANCELLED: 'Cancelado',
};

@Injectable({ providedIn: 'root' })
export class TicketsService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);

  listOwnTickets() {
    const token = this.session.user()?.token;
    return this.http.get<BuyerTicket[]>(`${environment.apiUrl}/tickets/me`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      transferCache: false,
    });
  }
}
