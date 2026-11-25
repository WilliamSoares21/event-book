import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import type { Event } from '../../../model/event.model';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { concatMap, from, tap, toArray } from 'rxjs';
import { EventsService, ImageUploadError } from '../../../services/events/events';
import type { TicketLotRequest } from '../../../model/ticket-lot.model';
import { ToastService } from '../../../services/toast/toast';

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
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly success = signal(false);
  private createdEventId?: string;
  private readonly createdLots = new Set<number>();
  readonly event: Event | undefined;
  private nextLotId = 1;
  lots: EventLot[] = [];
  private pendingCoverFile: File | null = null;

  constructor(location: Location, router: Router) {
    const state = (router.currentNavigation()?.extras.state ?? location.getState()) as
      { event?: Event; pendingCoverFile?: File } | null;
    this.event = state?.event;
    this.pendingCoverFile = state?.pendingCoverFile ?? null;
    this.addLot();
  }

  get today(): string {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
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
    this.toast.dismiss();
    if (!this.event) {
      this.showError('Volte e preencha os dados do evento.');
      return;
    }
    if (!this.lots.length) {
      this.showError('Adicione pelo menos um lote.');
      return;
    }

    const earliestDate = new Date(`${this.today}T00:00:00`);
    const pending: { id: number; payload: TicketLotRequest }[] = [];
    for (const lot of this.lots) {
      if (this.createdLots.has(lot.id)) continue;
      const start = new Date(`${lot.salesStart}T00:00:00`);
      const end = new Date(`${lot.salesEnd}T00:00:00`);
      if (!lot.name.trim() || lot.price === null || !Number.isFinite(lot.price) || lot.price < 0 ||
          lot.quantity === null || !Number.isInteger(lot.quantity) || lot.quantity <= 0 ||
          !Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
        this.showError('Preencha os lotes com nome, preço válido, quantidade positiva e data final posterior à inicial.');
        return;
      }
      if (start < earliestDate || end < earliestDate) {
        this.showError('As datas de venda devem ser de hoje em diante.');
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
          this.toast.show(this.message(), true);
          // Upload cover image if pending
          if (this.pendingCoverFile && this.createdEventId) {
            this.uploadCoverImage(this.createdEventId);
          } else {
            void this.router.navigate(['/events']);
          }
        },
        error: () => {
          this.saving.set(false);
          this.showError('O evento foi criado, mas houve erro ao criar um lote. Tente novamente para enviar os lotes pendentes.');
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
        this.showError('Não foi possível criar o evento. Verifique os dados e sua sessão e tente novamente.');
      },
    });
  }

  private uploadCoverImage(eventId: string) {
    const file = this.pendingCoverFile;
    if (!file) {
      void this.router.navigate(['/events']);
      return;
    }

    this.eventsService.uploadEventImage(eventId, file).subscribe({
      next: () => {
        this.toast.show('Imagem de capa enviada com sucesso!');
        void this.router.navigate(['/events']);
      },
      // O evento já existe: só o upload falhou, então não recriamos nada.
      error: (error) => {
        const motivo = error instanceof ImageUploadError ? error.message : 'erro ao processar a imagem.';
        this.toast.show(`Evento criado, mas ${motivo}`);
        console.error('Falha no upload da capa após criar o evento', {
          stage: error instanceof ImageUploadError ? error.stage : 'unknown',
          status: error instanceof ImageUploadError ? error.status : undefined,
        });
        void this.router.navigate(['/events']);
      },
    });
  }

  private showError(message: string) {
    this.message.set(message);
    this.toast.show(message);
  }
}
