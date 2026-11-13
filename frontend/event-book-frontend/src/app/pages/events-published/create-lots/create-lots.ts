import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import type { Event } from '../../../model/event.model';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { concatMap, from, tap, toArray } from 'rxjs';
import { EventsService } from '../../../services/events/events';
import type { TicketLotRequest } from '../../../model/ticket-lot.model';

interface EventLot {
  id: number;
  name: string;
  price: number | null;
  quantity: number | null;
  salesStart: string;
  salesEnd: string;
}

@Component({
  imports: [MenuSideBarComponent, RouterLink, FormsModule],
  selector: 'app-create-lots',
  styleUrl: './create-lots.css',
  templateUrl: './create-lots.html',
})
export class CreateLotsComponents {
  private readonly eventsService = inject(EventsService);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly success = signal(false);
  private createdEventId?: string;
  private readonly createdLots = new Set<number>();
  readonly event: Event | undefined;
  private nextLotId = 1;
  lots: EventLot[] = [];

  constructor(location: Location, router: Router) {
    const state = (router.currentNavigation()?.extras.state ?? location.getState()) as
      { event?: Event } | null;
    this.event = state?.event;
    this.addLot();
  }

  addLot() {
    if (this.saving() || this.success()) return;
    this.lots.push({
      id: this.nextLotId++,
      name: '',
      price: null,
      quantity: null,
      salesStart: '',
      salesEnd: '',
    });
  }

  removeLot(id: number) {
    if (this.saving() || this.createdLots.has(id) || this.success()) return;
    this.lots = this.lots.filter(lot => lot.id !== id);
  }

  isLotLocked(id: number) {
    return this.saving() || this.success() || this.createdLots.has(id);
  }

  createEvent() {
    if (this.saving() || this.success()) return;
    this.message.set('');
    if (!this.event) {
      this.message.set('Volte e preencha os dados do evento.');
      return;
    }
    if (!this.lots.length) {
      this.message.set('Adicione pelo menos um lote.');
      return;
    }

    const pending: { id: number; payload: TicketLotRequest }[] = [];
    for (const lot of this.lots) {
      if (this.createdLots.has(lot.id)) continue;
      const start = new Date(`${lot.salesStart}T00:00:00`);
      const end = new Date(`${lot.salesEnd}T00:00:00`);
      if (!lot.name.trim() || lot.price === null || !Number.isFinite(lot.price) || lot.price < 0 ||
          lot.quantity === null || !Number.isInteger(lot.quantity) || lot.quantity <= 0 ||
          !Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
        this.message.set('Preencha os lotes com nome, preço válido, quantidade positiva e data final posterior à inicial.');
        return;
      }
      pending.push({
        id: lot.id,
        payload: {
          name: lot.name.trim(),
          unitPrice: lot.price,
          capacity: lot.quantity,
          salesStart: start.toISOString(),
          salesEnd: end.toISOString(),
        },
      });
    }

    this.saving.set(true);
    const createLots = (eventId: string) => {
      from(pending).pipe(
        concatMap(lot => this.eventsService.createLot(eventId, lot.payload).pipe(
          tap(() => this.createdLots.add(lot.id)),
        )),
        toArray(),
      ).subscribe({
        next: () => {
          this.saving.set(false);
          this.success.set(true);
          this.message.set('Evento e lotes criados com sucesso!');
        },
        error: () => {
          this.saving.set(false);
          this.message.set('O evento foi criado, mas houve erro ao criar um lote. Tente novamente para enviar os lotes pendentes.');
        },
      });
    };

    if (this.createdEventId) {
      createLots(this.createdEventId);
      return;
    }
    this.eventsService.createEvent(this.event).subscribe({
      next: (response) => {
        this.createdEventId = response.id;
        createLots(response.id);
      },
      error: () => {
        this.saving.set(false);
        this.message.set('Não foi possível criar o evento. Verifique os dados e sua sessão e tente novamente.');
      },
    });
  }
}
