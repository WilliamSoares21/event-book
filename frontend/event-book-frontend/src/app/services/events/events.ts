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
  readonly httpClient = inject(HttpClient); // Exposed for direct R2 PUT
  private readonly session = inject(SessionService);
  private readonly apiUrl = environment.apiUrl;

  listPublishedEvents(page = 0, size = 10) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.httpClient.get<PageResponse<EventResponse>>(`${this.apiUrl}/events`, { params });
  }

  listOwnEvents() {
    return this.httpClient.get<EventListItem[]>(`${this.apiUrl}/organizer/events`, this.authOptions());
  }

  getEvent(eventId: string) {
    return this.httpClient.get<EventResponse>(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}`, this.authOptions(),
    );
  }

  listAssignedStaffEvents() {
    return this.httpClient.get<EventListItem[]>(`${this.apiUrl}/staff/events`, this.authOptions());
  }

  checkInTicket(code: string) {
    return this.httpClient.post<{ ticketCode: string; checkedInAt: string; staffId: string }>(
      `${this.apiUrl}/tickets/${encodeURIComponent(code)}/check-in`, null, this.authOptions(),
    );
  }

  listStaffForEvent(eventId: string) {
    return this.httpClient.get<EventStaffOption[]>(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}/staff`, this.authOptions(),
    );
  }

  addStaffToEvent(eventId: string, userId: string) {
    return this.httpClient.post(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}/staff`, { userId }, this.authOptions(),
    );
  }

  publishEvent(eventId: string) {
    return this.httpClient.post<void>(
      `${this.apiUrl}/events/${encodeURIComponent(eventId)}/publication`, null, this.authOptions(),
    );
  }

  createOrder(ticketLotId: string, quantity: number) {
    return this.httpClient.post<{ id: string }>(
      `${this.apiUrl}/orders`, { ticketLotId, quantity }, this.authOptions(),
    );
  }

  checkoutOrder(orderId: string, cardId: string) {
    return this.httpClient.post<unknown>(
      `${this.apiUrl}/orders/${encodeURIComponent(orderId)}/checkout`, { cardId }, this.authOptions(),
    );
  }

  getOrCreateSimulatedCard() {
    const options = this.authOptions();
    const findActiveCard = () => this.httpClient.get<SimulatedCard[]>(`${this.apiUrl}/cards/me`, options)
      .pipe(map((cards) => cards.find((card) => card.status === 'ACTIVE')));

    return findActiveCard().pipe(switchMap((card) => card ? of(card) :
      this.httpClient.post<SimulatedCard>(`${this.apiUrl}/cards`, null, options).pipe(
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
    return this.httpClient.post<EventResponse>(`${this.apiUrl}/events`, event, this.authOptions());
  }

  createLot(eventId: string, lot: TicketLotRequest) {
    return this.httpClient.post<TicketLotResponse>(
      `${this.apiUrl}/events/${eventId}/ticket-lots`, lot, this.authOptions(),
    );
  }

  private authOptions() {
    const token = this.session.user()?.token;
    return { headers: new HttpHeaders(token ? { Authorization: `Bearer ${token}` } : {}) };
  }

  getUploadUrl(eventId: string, request: EventImageUploadRequest) {
    return this.httpClient.post<EventImageUploadUrlResponse>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images/upload-url`, request, this.authOptions());
  }

  confirmUploadImage(eventId: string, request: EventImageConfirmRequest) {
    return this.httpClient.post<unknown>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images/confirm`, request, this.authOptions());
  }

  removeEventImage(eventId: string) {
    return this.httpClient.delete<void>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images`, this.authOptions());
  }

  getEventImage(eventId: string) {
    return this.httpClient.get<unknown>(`${this.apiUrl}/events/${encodeURIComponent(eventId)}/images`, this.authOptions());
  }

}