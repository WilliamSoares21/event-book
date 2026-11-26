import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import type { EventResponse, AvailableTicketLot } from '../../../model/event.model';
import type { OrderItemRequest } from '../../../model/order.model';
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

  readonly publicCatalog = this.route.snapshot.data['publicCatalog'] === true;
  readonly roleUser = computed(() => this.session.user()?.role);
  readonly event = signal<EventResponse | null>(null);
  readonly loadError = signal('');
  readonly publishing = signal(false);
  readonly publicationError = signal('');
  readonly published = signal(false);
  readonly ordering = signal(false);
  readonly orderFeedback = signal<{ message: string; error: boolean } | null>(null);
  readonly ticketPrice = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

  // Quantidades por lote: Map<lotId, quantity>
  readonly lotQuantities = signal<Map<string, number>>(new Map());

  readonly availableLots = computed(() =>
    this.event()?.availableTicketLots?.filter((lot) => lot.availableQuantity > 0) ?? [],
  );

  // Subtotal por lote
  readonly lotSubtotals = computed(() => {
    const map = new Map<string, number>();
    const quantities = this.lotQuantities();
    for (const lot of this.availableLots()) {
      const qty = quantities.get(lot.id) ?? 0;
      map.set(lot.id, qty * lot.unitPrice);
    }
    return map;
  });

  // Total geral
  readonly orderTotal = computed(() => {
    let total = 0;
    for (const subtotal of this.lotSubtotals().values()) {
      total += subtotal;
    }
    return total;
  });

  // Verifica se há pelo menos 1 item selecionado
  readonly hasSelection = computed(() => {
    for (const qty of this.lotQuantities().values()) {
      if (qty > 0) return true;
    }
    return false;
  });

  // Itens para envio ao backend
  readonly orderItems = computed((): OrderItemRequest[] => {
    const items: OrderItemRequest[] = [];
    const quantities = this.lotQuantities();
    for (const lot of this.availableLots()) {
      const qty = quantities.get(lot.id) ?? 0;
      if (qty > 0) {
        items.push({ ticketLotId: lot.id, quantity: qty });
      }
    }
    return items;
  });

  readonly statusLabels: Record<EventResponse['status'], string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicado',
    CANCELLED: 'Cancelado',
    FINISHED: 'Encerrado',
  };

  readonly canManageStaff = computed(() => {
    const user = this.session.user();
    return !this.publicCatalog && user?.role === 'ORGANIZADOR'
      && user.id === this.event()?.organizerId;
  });

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
        this.lotQuantities.set(new Map());
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
    ).subscribe((event) => {
      if (this.publicCatalog && event.status !== 'PUBLISHED') {
        this.loadError.set('Evento não encontrado.');
        return;
      }

      this.event.set(event);
    });
  }

  incrementQuantity(lot: AvailableTicketLot) {
    const current = this.lotQuantities().get(lot.id) ?? 0;
    if (current < lot.availableQuantity) {
      this.lotQuantities.update(map => {
        const newMap = new Map(map);
        newMap.set(lot.id, current + 1);
        return newMap;
      });
    }
  }

  decrementQuantity(lot: AvailableTicketLot) {
    const current = this.lotQuantities().get(lot.id) ?? 0;
    if (current > 0) {
      this.lotQuantities.update(map => {
        const newMap = new Map(map);
        newMap.set(lot.id, current - 1);
        return newMap;
      });
    }
  }

  onQuantityInput(lot: AvailableTicketLot, event: Event) {
    const input = event.target as HTMLInputElement;
    const value = Math.max(0, Math.min(lot.availableQuantity, input.valueAsNumber || 0));
    this.lotQuantities.update(map => {
      const newMap = new Map(map);
      if (value > 0) {
        newMap.set(lot.id, value);
      } else {
        newMap.delete(lot.id);
      }
      return newMap;
    });
  }

  getQuantity(lot: AvailableTicketLot): number {
    return this.lotQuantities().get(lot.id) ?? 0;
  }

  confirmOrder(dialog: HTMLDialogElement) {
    const event = this.event();
    const items = this.orderItems();
    if (this.roleUser() !== 'COMPRADOR' || event?.status !== 'PUBLISHED'
        || items.length === 0 || !dialog.open || this.ordering()) return;

    dialog.close();
    this.ordering.set(true);
    this.orderFeedback.set(null);
    let reservationCreated = false;
    let displayedEvent = event;
    this.eventsService.getOrCreateSimulatedCard().pipe(
      switchMap((card) => this.eventsService.createOrderGrouped(items).pipe(
        tap(() => {
          reservationCreated = true;
          if (this.event() !== displayedEvent) return;

          // Atualizar disponibilidade local
          displayedEvent = {
            ...event,
            availableTicketLots: event.availableTicketLots?.map((lot) => {
              const item = items.find(i => i.ticketLotId === lot.id);
              if (item) {
                return { ...lot, availableQuantity: lot.availableQuantity - item.quantity };
              }
              return lot;
            }),
          };
          this.event.set(displayedEvent);
          this.lotQuantities.set(new Map());
        }),
        switchMap((order) => this.eventsService.checkoutOrder(order.id, card.id)),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        if (this.event() !== displayedEvent) return;

        this.ordering.set(false);
        this.orderFeedback.set({ message: 'Compra aprovada. Seus ingressos estão em Meus tickets.', error: false });
      },
      error: (error) => {
        if (this.event() !== displayedEvent) return;

        this.ordering.set(false);
        const message = error.error?.error;
        this.orderFeedback.set({
          message: typeof message === 'string' && message
            ? message
            : reservationCreated
              ? 'O pedido foi reservado, mas não foi possível confirmar o pagamento.'
              : 'Não foi possível iniciar a compra.',
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