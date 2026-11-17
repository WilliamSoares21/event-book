import { SessionService } from '../../services/session/session';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { EventsService } from '../../services/events/events';
import type { EventListItem } from '../../model/event.model';

@Component({
  imports: [MenuSideBarComponent, CommonModule],
  selector: 'app-events',
  styleUrl: './events.css',
  templateUrl: './events.html',
})
export class EventsComponent implements OnInit {
  readonly session = inject(SessionService);
  private readonly eventsService = inject(EventsService);
  private readonly destroyRef = inject(DestroyRef);
  get roleUser() { return this.session.user()?.role; }
  readonly events = signal<EventListItem[]>([]);
  readonly loadError = signal('');

  constructor(private router: Router) {}

  ngOnInit() {
    const request = this.roleUser === 'ORGANIZADOR'
      ? this.eventsService.listOwnEvents()
      : this.eventsService.listPublishedEvents();

    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (events) => this.events.set(events),
      error: () => this.loadError.set('Não foi possível carregar os eventos. Tente novamente mais tarde.'),
    });
  }

  createEvent() {
    this.router.navigate(['/events/create']);
  }
}
