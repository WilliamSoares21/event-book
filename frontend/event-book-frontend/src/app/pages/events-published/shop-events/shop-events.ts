import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, Subject, catchError, debounceTime, map, merge, of, skip, startWith, switchMap, tap } from 'rxjs';
import type { EventListItem } from '../../../model/event.model';
import { EventsService } from '../../../services/events/events';

interface CatalogFilters {
  q: string;
  city: string;
  state: string;
}

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
  readonly loaded = signal(false);
  readonly searchTerm = signal('');
  readonly cityTerm = signal('');
  readonly stateTerm = signal('');
  readonly skeletonCards = [0, 1, 2];

  private readonly filters$ = new Subject<CatalogFilters>();
  private readonly page$ = new BehaviorSubject<number>(0);
  /** Filtros da última consulta aplicada (usados ao paginar). */
  private appliedFilters: CatalogFilters = { q: '', city: '', state: '' };

  ngOnInit() {
    const filterRequests$ = this.filters$.pipe(
      debounceTime(300),
      startWith({ q: '', city: '', state: '' }),
      tap(filters => {
        this.appliedFilters = filters;
        this.page.set(0);
      }),
      map(filters => ({ filters, page: 0 })),
    );

    const pageRequests$ = this.page$.pipe(
      skip(1),
      map(page => ({ filters: this.appliedFilters, page })),
    );

    merge(filterRequests$, pageRequests$)
      .pipe(
        switchMap(({ filters, page }) => this.fetch(filters, page)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(result => {
        if (result.ok) {
          this.events.set(result.response.content as EventListItem[]);
          this.totalPages.set(result.response.totalPages);
          this.loadError.set(false);
          this.loaded.set(true);
        } else {
          this.loadError.set(true);
        }
        this.loading.set(false);
      });
  }

  onFilterChange(e: Event) {
    this.searchTerm.set((e.target as HTMLInputElement).value);
    this.emitFilters();
  }

  onCityChange(e: Event) {
    this.cityTerm.set((e.target as HTMLInputElement).value);
    this.emitFilters();
  }

  onStateChange(e: Event) {
    this.stateTerm.set((e.target as HTMLInputElement).value);
    this.emitFilters();
  }

  /** Repete a consulta atual (usado pelo botão "Tentar novamente"). */
  loadEvents() {
    this.page$.next(this.page());
  }

  nextPage() {
    if (this.page() < this.totalPages() - 1) {
      const next = this.page() + 1;
      this.page.set(next);
      this.page$.next(next);
    }
  }

  prevPage() {
    if (this.page() > 0) {
      const previous = this.page() - 1;
      this.page.set(previous);
      this.page$.next(previous);
    }
  }

  private emitFilters() {
    this.filters$.next({
      q: this.searchTerm().trim(),
      city: this.cityTerm().trim(),
      state: this.stateTerm().trim(),
    });
  }

  private fetch(filters: CatalogFilters, page: number) {
    this.loading.set(true);
    return this.eventsService.listAvailableEvents(page, 10, filters).pipe(
      map((response) => ({ ok: true as const, response })),
      catchError(() => of({ ok: false as const, response: null })),
    );
  }
}
