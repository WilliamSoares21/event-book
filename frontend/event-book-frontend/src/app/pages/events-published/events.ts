import { SessionService } from '../../services/session/session';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { EventsService } from '../../services/events/events';
import type { EventListItem, EventResponse } from '../../model/event.model';
import { PageResponse } from '../../model/page-response.model';

@Component({
  imports: [MenuSideBarComponent, CommonModule, RouterLink],
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
  readonly loaded = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);

  constructor(private router: Router) {}

  ngOnInit() {
    this.loadEvents();
  }

  loadEvents() {
    this.loading.set(true);
    this.loadError.set('');

    const isOrganizer = this.roleUser === 'ORGANIZADOR';
    
    if (isOrganizer) {
      this.eventsService.listOwnEvents()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response: EventListItem[]) => {
            this.events.set(response);
            this.loaded.set(true);
            this.loading.set(false);
          },
          error: () => {
            this.loadError.set('Não foi possível carregar os eventos. Tente novamente mais tarde.');
            this.loading.set(false);
          },
        });
    } else {
      this.eventsService.listPublishedEvents(this.page(), 10)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response: PageResponse<EventResponse>) => {
            this.events.set(response.content as EventListItem[]);
            this.totalPages.set(response.totalPages);
            this.loaded.set(true);
            this.loading.set(false);
          },
          error: () => {
            this.loadError.set('Não foi possível carregar os eventos. Tente novamente mais tarde.');
            this.loading.set(false);
          },
        });
    }
  }

  nextPage() {
    if (this.page() < this.totalPages() - 1) {
      this.page.update(p => p + 1);
      this.loadEvents();
    }
  }

  prevPage() {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.loadEvents();
    }
  }

  createEvent() {
    this.router.navigate(['/events/create']);
  }
}
