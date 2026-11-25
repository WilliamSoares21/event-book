import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import type { EventListItem, EventResponse } from '../../../model/event.model';
import { EventsService } from '../../../services/events/events';
import { PageResponse } from '../../../model/page-response.model';

@Component({
  imports: [DatePipe, RouterLink],
  selector: 'app-shop-events',
  styleUrl: './shop-events.css',
  templateUrl: './shop-events.html',
})
export class ShopEvents implements OnInit {
  private readonly eventsService = inject(EventsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly events = signal<EventListItem[] | null>(null);
  readonly loadError = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  private nameFilter = '';
  private cityFilter = '';
  private stateFilter = '';

  ngOnInit() {
    this.loadEvents();
  }

  onFilterChange(e: Event) { this.nameFilter = (e.target as HTMLInputElement).value; this.loadEvents(); }
  onCityChange(e: Event) { this.cityFilter = (e.target as HTMLInputElement).value; this.loadEvents(); }
  onStateChange(e: Event) { this.stateFilter = (e.target as HTMLInputElement).value; this.loadEvents(); }

  loadEvents() {
    this.loading.set(true);
    this.eventsService.listPublishedEvents(this.page(), 10) // Nota: API original não tem filtros por nome/cidade na assinatura, mas o backend suporta query params
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response: PageResponse<EventResponse>) => {
          this.events.set(response.content as EventListItem[]);
          this.totalPages.set(response.totalPages);
          this.loadError.set(false);
          this.loading.set(false);
        },
        error: () => {
          this.loadError.set(true);
          this.loading.set(false);
        },
      });
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
}
