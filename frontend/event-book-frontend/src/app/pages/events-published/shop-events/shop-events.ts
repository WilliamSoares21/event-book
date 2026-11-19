import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import type { EventListItem } from '../../../model/event.model';
import { EventsService } from '../../../services/events/events';

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

  ngOnInit() {
    this.eventsService.listPublishedEvents()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (events) => this.events.set(events),
        error: () => this.loadError.set(true),
      });
  }
}
