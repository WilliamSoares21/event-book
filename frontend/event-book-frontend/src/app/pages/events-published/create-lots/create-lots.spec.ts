import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CreateLotsComponents } from './create-lots';
import { SessionService } from '../../../services/session/session';
import { environment } from '../../../../environments/environment';

const event = {
  title: 'Conferência', description: 'Tecnologia', location: 'Fortaleza',
  startsAt: '2026-12-10T12:00:00Z', endsAt: '2026-12-11T21:00:00Z',
};
const base = `${environment.apiUrl.replace(/\/$/, '').replace(/\/api$/, '')}/api`;

describe('CreateLotsComponents requests', () => {
  let component: CreateLotsComponents;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: Location, useValue: { getState: () => ({ event }) } }],
    });
    TestBed.inject(SessionService).user.set({
      id: 'organizer', role: 'ORGANIZADOR', name: 'Organizador', email: 'test@example.com', token: 'test-token',
    });
    http = TestBed.inject(HttpTestingController);
    component = TestBed.createComponent(CreateLotsComponents).componentInstance;
    Object.assign(component.lots[0], {
      name: 'Primeiro lote', price: 50, quantity: 100, salesStart: '2026-12-01', salesEnd: '2026-12-09',
    });
    component.addLot();
    Object.assign(component.lots[1], {
      name: 'Segundo lote', price: 75, quantity: 200, salesStart: '2026-12-01', salesEnd: '2026-12-10',
    });
  });

  afterEach(() => http.verify());

  it('creates the event first, then each lot with the returned ID and mapped inputs', () => {
    component.createEvent();
    component.createEvent(); // Bloqueia envio duplicado durante a requisição.
    http.expectNone(`${base}/events/event-id/ticket-lots`);
    const request = http.expectOne(`${base}/events`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(event);
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({ ...event, id: 'event-id' });
    const first = http.expectOne(`${base}/events/event-id/ticket-lots`);
    expect(first.request.body).toEqual({
      name: 'Primeiro lote', unitPrice: 50, capacity: 100,
      salesStart: new Date('2026-12-01T00:00:00').toISOString(),
      salesEnd: new Date('2026-12-09T00:00:00').toISOString(),
    });
    first.flush({ id: 'lot-1' });
    const second = http.expectOne(`${base}/events/event-id/ticket-lots`);
    expect(second.request.body.name).toBe('Segundo lote');
    second.flush({ id: 'lot-2' });
    expect(component.success()).toBe(true);
    expect(component.saving()).toBe(false);
  });

  it('does not create lots when the event request fails', () => {
    component.createEvent();
    http.expectOne(`${base}/events`).flush({}, { status: 422, statusText: 'Invalid event' });
    http.expectNone(req => req.url.includes('ticket-lots'));
    expect(component.success()).toBe(false);
    expect(component.saving()).toBe(false);
    expect(component.message()).toContain('Não foi possível criar o evento');
  });

  it('retries only pending lots after a partial failure', () => {
    component.createEvent();
    http.expectOne(`${base}/events`).flush({ ...event, id: 'event-id' });
    http.expectOne(`${base}/events/event-id/ticket-lots`).flush({ id: 'lot-1' });
    http.expectOne(`${base}/events/event-id/ticket-lots`).flush({}, { status: 500, statusText: 'Error' });
    expect(component.success()).toBe(false);
    expect(component.saving()).toBe(false);
    component.createEvent();
    http.expectNone(`${base}/events`);
    const retry = http.expectOne(`${base}/events/event-id/ticket-lots`);
    expect(retry.request.body.name).toBe('Segundo lote');
    retry.flush({ id: 'lot-2' });
    expect(component.success()).toBe(true);
  });
});
