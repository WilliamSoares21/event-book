import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import type { Event, EventListItem, EventResponse } from '../../model/event.model';
import type { TicketLotRequest, TicketLotResponse } from '../../model/ticket-lot.model';
import { SessionService } from '../session/session';
import { catchError, map, of, switchMap, throwError } from 'rxjs';
import { PageResponse } from '../../model/page-response.model';

interface SimulatedCard {
  id: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface EventStaffOption {
  id: string;
  name: string;
  associated: boolean;
}

import type { EventImageUploadRequest, EventImageUploadUrlResponse, EventImageConfirmRequest } from "../../model/event-image.model";
@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly apiUrl = environment.apiUrl;

  listPublishedEvents(page = 0, size = 10) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<EventResponse>>(`${this.apiUrl}/events`, { params });
  }

  listOwnEvents() {
    return this.http.get<EventListItem[]>(`${this.apiUrl}/organizer/events`, this.authOptions());
  }

  getEvent(eventId: string) {
    return this.http.get<EventResponse>(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}`, this.authOptions(),
    );
  }

  listAssignedStaffEvents() {
    return this.http.get<EventListItem[]>(`${this.apiUrl}/staff/events`, this.authOptions());
  }

  checkInTicket(code: string) {
    return this.http.post<{ ticketCode: string; checkedInAt: string; staffId: string }>(
      `${this.apiUrl}/tickets/${encodeURIComponent(code)}/check-in`, null, this.authOptions(),
    );
  }

  listStaffForEvent(eventId: string) {
    return this.http.get<EventStaffOption[]>(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}/staff`, this.authOptions(),
    );
  }

  addStaffToEvent(eventId: string, userId: string) {
    return this.http.post(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}/staff`, { userId }, this.authOptions(),
    );
  }

  publishEvent(eventId: string) {
    return this.http.post<void>(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}/publication`, null, this.authOptions(),
    );
  }

  createOrder(ticketLotId: string, quantity: number) {
    return this.http.post<{ id: string }>(
      `${this.apiUrl}/orders`, { ticketLotId, quantity }, this.authOptions(),
    );
  }

  checkoutOrder(orderId: string, cardId: string) {
    return this.http.post<unknown>(
      `${this.apiUrl}/orders/${encodeURIComponent(orderId)}/checkout`, { cardId }, this.authOptions(),
    );
  }

  getOrCreateSimulatedCard() {
    const options = this.authOptions();
    const findActiveCard = () => this.http.get<SimulatedCard[]>(`${this.apiUrl}/cards/me`, options)
      .pipe(map((cards) => cards.find((card) => card.status === 'ACTIVE')));

    return findActiveCard().pipe(switchMap((card) => card ? of(card) :
      this.http.post<SimulatedCard>(`${this.apiUrl}/cards`, null, options).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status !== 409) return throwError(() => error);
          return findActiveCard().pipe(map((activeCard) => {
            if (!activeCard) throw error;
            return activeCard;
          }));
        }),
      ),
    ));
  }

  createEvent(event: Event) {
    return this.http.post<EventResponse>(`${this.apiUrl}/events`, event, this.authOptions());
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

  getUploadUrl(eventId: string, request: EventImageUploadRequest) {
    return this.http.post<EventImageUploadUrlResponse>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images/upload-url`, request, this.authOptions());
  }

  confirmUploadImage(eventId: string, request: EventImageConfirmRequest) {
    return this.http.post<unknown>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images/confirm`, request, this.authOptions());
  }

  removeEventImage(eventId: string) {
    return this.http.delete<void>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images`, this.authOptions());
  }

  getEventImage(eventId: string) {
    return this.http.get<unknown>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images`, this.authOptions());
  }

}