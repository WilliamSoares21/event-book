import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, switchMap } from 'rxjs';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import type { EventResponse } from '../../../model/event.model';
import { EventsService } from '../../../services/events/events';
import { SessionService } from '../../../services/session/session';

@Component({
  imports: [DatePipe, MenuSideBarComponent, RouterLink],
  selector: 'app-details-events',
  styleUrl: './details-events.css',
  templateUrl: './details-events.html',
})
export class DetailsEvents implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly eventsService = inject(EventsService);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly roleUser = computed(() => this.session.user()?.role);
  readonly event = signal<EventResponse | null>(null);
  readonly loadError = signal('');
  readonly publishing = signal(false);
  readonly publicationError = signal('');
  readonly published = signal(false);
  readonly quantity = signal(1);
  readonly ordering = signal(false);
  readonly orderFeedback = signal<{ message: string; error: boolean } | null>(null);
  readonly ticketPrice = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  readonly availableLots = computed(() =>
    this.event()?.availableTicketLots?.filter((lot) => lot.availableQuantity > 0) ?? [],
  );
  readonly maxQuantity = computed(() =>
    Math.max(0, ...this.availableLots().map((lot) => lot.availableQuantity)),
  );

  readonly orderSelection = computed(() => {
    const quantity = this.quantity();
    if (!Number.isInteger(quantity) || quantity < 1) return null;

    const lot = this.availableLots().find((entry) => entry.availableQuantity >= quantity);
    return lot ? { ticketLotId: lot.id, quantity } : null;
  });

  readonly statusLabels: Record<EventResponse['status'], string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicado',
    CANCELLED: 'Cancelado',
    FINISHED: 'Encerrado',
  };

  readonly canPublish = computed(() => {
    const event = this.event();
    const user = this.session.user();
    return event?.status === 'DRAFT' && user?.role === 'ORGANIZADOR' && user.id === event.organizerId;
  });

  ngOnInit() {
    this.route.paramMap.pipe(
      switchMap((params) => {
        this.event.set(null);
        this.loadError.set('');
        this.publishing.set(false);
        this.publicationError.set('');
        this.published.set(false);
        this.quantity.set(1);
        this.ordering.set(false);
        this.orderFeedback.set(null);

        return this.eventsService.getEvent(params.get('id')!).pipe(
          catchError((error) => {
            this.loadError.set(error.status === 404
              ? 'Evento não encontrado.'
              : 'Não foi possível carregar os detalhes do evento.');
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((event) => this.event.set(event));
  }

  confirmOrder(dialog: HTMLDialogElement) {
    const event = this.event();
    const selection = this.orderSelection();
    if (this.roleUser() !== 'COMPRADOR' || event?.status !== 'PUBLISHED'
        || !selection || !dialog.open || this.ordering()) return;

    dialog.close();
    this.ordering.set(true);
    this.orderFeedback.set(null);
    this.eventsService.createOrder(selection.ticketLotId, selection.quantity)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          if (this.event() !== event) return;

          this.event.set({
            ...event,
            availableTicketLots: this.availableLots().map((lot) => lot.id === selection.ticketLotId
              ? { ...lot, availableQuantity: lot.availableQuantity - selection.quantity }
              : lot),
          });
          this.quantity.set(1);
          this.ordering.set(false);
          this.orderFeedback.set({ message: 'Pedido criado. Pagamento pendente.', error: false });
        },
        error: (error) => {
          if (this.event() !== event) return;

          this.ordering.set(false);
          const message = error.error?.error;
          this.orderFeedback.set({
            message: typeof message === 'string' && message
              ? message
              : 'Não foi possível criar o pedido.',
            error: true,
          });
        },
      });
  }

  publishEvent() {
    const event = this.event();
    if (!event || !this.canPublish() || this.publishing()) return;

    this.publishing.set(true);
    this.publicationError.set('');

    this.eventsService.publishEvent(event.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        if (this.event() !== event) return;

        this.event.set({ ...event, status: 'PUBLISHED' });
        this.published.set(true);
        this.publishing.set(false);
      },
      error: (error) => {
        if (this.event() !== event) return;

        this.publishing.set(false);
        const message = error.error?.error;
        this.publicationError.set(typeof message === 'string' && message
          ? message
          : 'Não foi possível publicar o evento. Tente novamente.');
      },
    });
  }
}
