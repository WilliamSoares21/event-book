import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CreateEventsComponent } from './create-events';
import { CreateLotsComponents } from '../create-lots/create-lots';
import { AcessoGuard } from '../../../guard/AcessoGuard';
import { ToastService } from '../../../services/toast/toast';
import { SessionService } from '../../../services/session/session';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Create event navigation', () => {
  afterEach(() => vi.useRealTimers());

  it('restores every input after going to lots and clicking Voltar', async () => {
    vi.setSystemTime(new Date(2026, 11, 10, 12));
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
    const component = await harness.navigateByUrl('/events/create', CreateEventsComponent);
    const page = harness.routeNativeElement!;
    for (const id of ['eventStartedDate', 'eventEndDate']) {
      expect(page.querySelector<HTMLInputElement>(`#${id}`)?.min).toBe('2026-12-10');
    }

    component.singUpForm.patchValue({ eventStartedDate: '2026-12-09' });
    page.querySelector<HTMLButtonElement>('button.btn-primary')!.click();
    expect(TestBed.inject(Router).url).toBe('/events/create');
    expect(TestBed.inject(ToastService).notification()?.message)
      .toBe('As datas do evento devem ser de hoje em diante.');
    const values = {
      eventName: 'Conferência',
      eventDescription: 'Encontro de tecnologia',
      venueName: 'Centro de Convenções',
      street: 'Av. Principal',
      number: '100',
      complement: 'Sala 1',
      neighborhood: 'Centro',
      city: 'Fortaleza',
      state: 'CE',
      zipCode: '60000-000',
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
    expect(updatedLots.event?.city).toBe(values.city);
    expect(updatedLots.event?.state).toBe(values.state);
  });
});
