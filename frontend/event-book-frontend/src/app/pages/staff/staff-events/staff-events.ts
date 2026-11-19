import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import type { EventListItem } from '../../../model/event.model';
import { EventsService } from '../../../services/events/events';
import { SessionService } from '../../../services/session/session';

@Component({
  imports: [DatePipe, MenuSideBarComponent, RouterLink],
  selector: 'app-staff-events',
  styleUrl: './staff-events.css',
  templateUrl: './staff-events.html',
})
export class StaffEvents implements OnInit {
  private readonly eventsService = inject(EventsService);
  private readonly destroyRef = inject(DestroyRef);
  readonly session = inject(SessionService);

  readonly events = signal<EventListItem[] | null>(null);
  readonly loadError = signal(false);

  ngOnInit() {
    this.eventsService.listAssignedStaffEvents()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (events) => this.events.set(events),
        error: () => this.loadError.set(true),
      });
  }
}
