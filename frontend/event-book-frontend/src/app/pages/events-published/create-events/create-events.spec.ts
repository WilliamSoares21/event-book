import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CreateEventsComponent } from './create-events';
import { CreateLotsComponents } from '../create-lots/create-lots';
import { AcessoGuard } from '../../../guard/AcessoGuard';
import { SessionService } from '../../../services/session/session';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Create event navigation', () => {
  it('restores every input after going to lots and clicking Voltar', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([
        { path: 'events/create', component: CreateEventsComponent, canActivate: [AcessoGuard] },
        { path: 'events/lots/create', component: CreateLotsComponents, canActivate: [AcessoGuard] },
      ])],
    });
    TestBed.inject(SessionService).user.set({
      id: 'organizer', role: 'ORGANIZADOR', name: 'Organizador', email: 'organizer@example.com',
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/events/create', CreateEventsComponent);
    const values = {
      eventName: 'Conferência',
      eventDescription: 'Encontro de tecnologia',
      eventLocation: 'Fortaleza',
      eventStartedDate: '2026-12-10',
      eventStartedTime: '09:30',
      eventEndDate: '2026-12-11',
      eventEndTime: '18:45',
    };
    for (const [name, value] of Object.entries(values)) {
      const input = harness.routeNativeElement!.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('button.btn-primary')!.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    const lots = harness.routeDebugElement!.componentInstance as CreateLotsComponents;
    expect(lots.event?.title).toBe(values.eventName);
    harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a.btn-cancel')!.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/events/create');
    for (const [name, value] of Object.entries(values)) {
      const input = harness.routeNativeElement!.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      expect(input.value).toBe(value);
    }
    const title = harness.routeNativeElement!.querySelector<HTMLInputElement>('#eventName')!;
    title.value = 'Conferência atualizada';
    title.dispatchEvent(new Event('input', { bubbles: true }));
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('button.btn-primary')!.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/events/lots/create');
    const updatedLots = harness.routeDebugElement!.componentInstance as CreateLotsComponents;
    expect(updatedLots.event?.title).toBe('Conferência atualizada');
    expect(updatedLots.event?.location).toBe(values.eventLocation);
  });
});
