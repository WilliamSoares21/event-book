import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import type { Event } from '../../model/event.model';
import type { TicketLotRequest, TicketLotResponse } from '../../model/ticket-lot.model';
import { SessionService } from '../session/session';

@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly apiUrl = `${environment.apiUrl.replace(/\/$/, '').replace(/\/api$/, '')}/api`;

  createEvent(event: Event) {
    return this.http.post<Event & { id: string }>(`${this.apiUrl}/events`, event, this.authOptions());
  }

  createLot(eventId: string, lot: TicketLotRequest) {
    return this.http.post<TicketLotResponse>(
      `${this.apiUrl}/events/${eventId}/ticket-lots`, lot, this.authOptions(),
    );
  }

  private authOptions() {
    const token = this.session.user()?.token;
    return { headers: new HttpHeaders(token ? { Authorization: `Bearer ${token}` } : {}) };
  }
}
