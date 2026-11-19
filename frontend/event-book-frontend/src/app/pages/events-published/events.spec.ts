import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { EventsComponent } from './events';
import { SessionService } from '../../services/session/session';

describe('EventsComponent listing', () => {
  let http: HttpTestingController;
  const user = signal({ role: 'ORGANIZADOR', token: 'test-token' });

  beforeEach(async () => {
    user.set({ role: 'ORGANIZADOR', token: 'test-token' });
    await TestBed.configureTestingModule({
      imports: [EventsComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SessionService, useValue: { user } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  for (const role of ['ORGANIZADOR', 'COMPRADOR', 'VISITANTE', 'STAFF']) {
    it(`loads the appropriate endpoint and renders card data for ${role}`, () => {
      user.set({ role, token: 'test-token' });
      const fixture = TestBed.createComponent(EventsComponent);
      fixture.detectChanges();
      const path = role === 'ORGANIZADOR' ? '/api/organizer/events' : '/api/events';
      const request = http.expectOne((req) => req.url.endsWith(path));
      expect(request.request.method).toBe('GET');
      expect(request.request.headers.get('Authorization')).toBe(
        role === 'ORGANIZADOR' ? 'Bearer test-token' : null,
      );
      request.flush([{
        id: 'event-1',
        title: 'Encontro de tecnologia', startsAt: '2027-06-15T18:30:00',
        location: 'Centro de convenções',
        status: role === 'ORGANIZADOR' ? 'DRAFT' : 'PUBLISHED',
      }]);
      fixture.detectChanges();
      const card = fixture.nativeElement.querySelector('.card');
      expect(card.querySelector('.card-title').textContent).toContain('Encontro de tecnologia');
      expect(card.querySelector('a').getAttribute('href')).toBe('/events/event-1');
      expect(Array.from(card.querySelectorAll('.card-text'), (el: any) => el.textContent.trim()))
        .toEqual(['15/06/2027', '18:30', 'Centro de convenções']);
      expect(fixture.nativeElement.querySelectorAll('.page-item').length).toBe(5);
    });
  }

  it('leaves the listing empty when there are no events', () => {
    const fixture = TestBed.createComponent(EventsComponent);
    fixture.detectChanges();
    http.expectOne((req) => req.url.endsWith('/api/organizer/events')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.card').length).toBe(0);
  });

  it('shows an error without falling back to public events when organizer authorization fails', () => {
    const fixture = TestBed.createComponent(EventsComponent);
    fixture.detectChanges();
    http.expectOne((req) => req.url.endsWith('/api/organizer/events'))
      .flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Não foi possível carregar os eventos');
    expect(fixture.nativeElement.querySelectorAll('.card').length).toBe(0);
  });
});
