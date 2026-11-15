import { Component, inject } from '@angular/core';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { Router, RouterLink } from '@angular/router';
import { SessionService } from '../../../services/session/session';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import type { Event } from '../../../model/event.model';

@Component({
  imports: [MenuSideBarComponent, RouterLink, ReactiveFormsModule],
  selector: 'app-create-events',
  styleUrl: './create-events.css',
  templateUrl: './create-events.html',
})
export class CreateEventsComponent {
  private readonly session = inject(SessionService);
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
      eventLocation: ['']
    });

    const state = (this.router.currentNavigation()?.extras.state ?? location.getState()) as
      { event?: Event } | null;
    if (state?.event) {
      const event = state.event;
      const start = this.toLocalFields(event.startsAt);
      const end = this.toLocalFields(event.endsAt);
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

  private toLocalFields(value: string): { date: string; time: string } {
    const date = new Date(value);
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
    const event = {
      title: this.singUpForm.get('eventName')?.value,
      description: this.singUpForm.get('eventDescription')?.value,
      startsAt: this.toDateTime('eventStartedDate', 'eventStartedTime'),
      endsAt: this.toDateTime('eventEndDate', 'eventEndTime'),
      location: this.singUpForm.get('eventLocation')?.value
    };

    this.router.navigate(['/events/lots/create'], { state: { event } });
  }
}
