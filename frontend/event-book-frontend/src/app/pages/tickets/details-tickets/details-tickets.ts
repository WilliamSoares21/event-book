import { Component, inject } from '@angular/core';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { SessionService } from '../../../services/session/session';

@Component({
  imports: [MenuSideBarComponent],
  selector: 'app-details-tickets',
  styleUrl: './details-tickets.css',
  templateUrl: './details-tickets.html',
})
export class DetailsTickets {
  readonly session = inject(SessionService);
  get roleUser() { return this.session.user()?.role; }
  ticket = {
    name: 'Event 1',
    day: '12/12/2023',
    time: '10:00 AM',
    codigo: 'aldhajdhaj'
  };
}
