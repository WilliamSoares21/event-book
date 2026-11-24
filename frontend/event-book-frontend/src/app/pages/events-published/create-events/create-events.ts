import { Component, inject } from '@angular/core';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { Router, RouterLink } from '@angular/router';
import { SessionService } from '../../../services/session/session';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { ToastService } from '../../../services/toast/toast';
import type { Event } from '../../../model/event.model';

@Component({
  imports: [MenuSideBarComponent, RouterLink, ReactiveFormsModule],
  selector: 'app-create-events',
  styleUrl: './create-events.css',
  templateUrl: './create-events.html',
})
export class CreateEventsComponent {
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);
  get today(): string { return this.toLocalFields(new Date()).date; }
  get roleUser() { return this.session.user()?.role; }

  singUpForm: FormGroup;

  constructor(private router: Router, private fb: FormBuilder, location: Location) {
    this.singUpForm = this.fb.group({
      eventName: [''],
      eventDescription: [''],
      eventStartedDate: [''],
      eventEndDate: [''],
      eventStartedTime: [''],
      eventEndTime: [''],
      venueName: [''],
      street: [''],
      number: [''],
      complement: [''],
      neighborhood: [''],
      city: [''],
      state: [''],
      zipCode: ['']
    });

    const state = (this.router.currentNavigation()?.extras.state ?? location.getState()) as
      { event?: Event } | null;
    if (state?.event) {
      const event = state.event;
      const start = this.toLocalFields(new Date(event.startsAt));
      const end = this.toLocalFields(new Date(event.endsAt));
      this.singUpForm.patchValue({
        eventName: event.title,
        eventDescription: event.description,
        eventLocation: event.location,
        eventStartedDate: start.date,
        eventStartedTime: start.time,
        eventEndDate: end.date,
        eventEndTime: end.time,
      });
    }
  }

  private toLocalFields(date: Date): { date: string; time: string } {
    if (Number.isNaN(date.getTime())) {
      return { date: '', time: '' };
    }

    const pad = (part: number) => String(part).padStart(2, '0');
    return {
      date: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
      time: `${pad(date.getHours())}:${pad(date.getMinutes())}`,
    };
  }

  private toDateTime(dateControl: string, timeControl: string): string {
    const date = this.singUpForm.get(dateControl)?.value;
    const time = this.singUpForm.get(timeControl)?.value;
    return date && time ? new Date(`${date}T${time}`).toISOString() : '';
  }

  createLots() {
    const { eventStartedDate, eventEndDate } = this.singUpForm.value;
    const earliestDate = new Date(`${this.today}T00:00:00`);
    if (new Date(`${eventStartedDate}T00:00:00`) < earliestDate ||
        new Date(`${eventEndDate}T00:00:00`) < earliestDate) {
      this.toast.show('As datas do evento devem ser de hoje em diante.');
      return;
    }

    this.toast.dismiss();
    const event = {
      title: this.singUpForm.get('eventName')?.value,
      description: this.singUpForm.get('eventDescription')?.value,
      startsAt: this.toDateTime('eventStartedDate', 'eventStartedTime'),
      endsAt: this.toDateTime('eventEndDate', 'eventEndTime'),
      venueName: this.singUpForm.get('venueName')?.value,
      street: this.singUpForm.get('street')?.value,
      number: this.singUpForm.get('number')?.value,
      complement: this.singUpForm.get('complement')?.value,
      neighborhood: this.singUpForm.get('neighborhood')?.value,
      city: this.singUpForm.get('city')?.value,
      state: this.singUpForm.get('state')?.value,
      zipCode: this.singUpForm.get('zipCode')?.value
    };

    this.router.navigate(['/events/lots/create'], { state: { event } });
  }
}
