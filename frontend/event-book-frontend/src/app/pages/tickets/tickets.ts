import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import { SessionService } from '../../services/session/session';
import { BuyerTicket, TicketsService, ticketStatusLabels } from '../../services/tickets/tickets';

@Component({
  imports: [MenuSideBarComponent, CurrencyPipe, DatePipe, NgClass, RouterLink],
  selector: 'app-tickets',
  styleUrl: './tickets.css',
  templateUrl: './tickets.html',
})
export class TicketsComponent implements OnInit {
  private readonly session = inject(SessionService);
  private readonly ticketsService = inject(TicketsService);
  private readonly destroyRef = inject(DestroyRef);

  get roleUser() { return this.session.user()?.role; }

  readonly tickets = signal<BuyerTicket[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly statusLabels = ticketStatusLabels;

  ngOnInit() {
    if (this.roleUser !== 'COMPRADOR') {
      this.error.set('A listagem de tickets é exclusiva para compradores.');
      return;
    }

    this.loading.set(true);
    this.ticketsService.listOwnTickets().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Não foi possível carregar seus tickets.');
        this.loading.set(false);
      },
    });
  }
}
