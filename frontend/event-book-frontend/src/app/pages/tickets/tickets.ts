import { Component, inject } from '@angular/core';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import { CommonModule } from '@angular/common';
import { SessionService } from '../../services/session/session';

@Component({
  imports: [MenuSideBarComponent, CommonModule],
  selector: 'app-tickets',
  styleUrl: './tickets.css',
  templateUrl: './tickets.html',
})
export class TicketsComponent {
  readonly session = inject(SessionService);
  get roleUser() { return this.session.user()?.role; }
  tickets = [
    {
      name: 'Event 1',
      day: '12/12/2023',
      time: '10:00 AM',
      codigo: 'aldhajdhaj'
    },
    {
      name: 'Event 2',
      day: '06/06/2023',
      time: '2:00 PM',
      codigo: 'lkdhjakdkd'
    },
    {
      name: 'Event 4',
      day: '04/04/2023',
      time: '4:00 PM',
      codigo: 'ladaiuduaidg'
    },
    {
      name: 'Event 5',
      day: '05/05/2023',
      time: '6:00 PM',
      codigo: 'akdgakdbd'
    }
  ];
}
