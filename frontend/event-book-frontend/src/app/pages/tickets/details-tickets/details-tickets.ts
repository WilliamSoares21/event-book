import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toString as renderQrCode } from 'qrcode';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { SessionService } from '../../../services/session/session';
import { TicketsService, ticketStatusLabels } from '../../../services/tickets/tickets';

@Component({
  imports: [AsyncPipe, DatePipe, MenuSideBarComponent, RouterLink],
  selector: 'app-details-tickets',
  styleUrl: './details-tickets.css',
  templateUrl: './details-tickets.html',
})
export class DetailsTickets {
  private readonly session = inject(SessionService);
  private readonly ticketsService = inject(TicketsService);
  private readonly route = inject(ActivatedRoute);

  get roleUser() { return this.session.user()?.role; }

  readonly statusLabels = ticketStatusLabels;
  readonly details = toSignal(this.route.paramMap.pipe(
    switchMap((params) => {
      if (this.roleUser !== 'COMPRADOR') {
        return of({ ticket: null, error: 'Os detalhes do ticket são exclusivos para compradores.' });
      }

      return this.ticketsService.listOwnTickets().pipe(
        map((tickets) => {
          const ticket = tickets.find((item) => item.id === params.get('id')) ?? null;
          return { ticket, error: ticket ? '' : 'Ticket não encontrado.' };
        }),
        catchError(() => of({ ticket: null, error: 'Não foi possível carregar o ticket.' })),
        startWith(null),
      );
    }),
  ));

  readonly qrImage = computed(() => {
    const code = this.details()?.ticket?.uniqueCode;
    if (!code) return null;

    return renderQrCode(code, { type: 'svg' })
      .then((svg) => `data:image/svg+xml,${encodeURIComponent(svg)}`)
      .catch(() => null);
  });
}
