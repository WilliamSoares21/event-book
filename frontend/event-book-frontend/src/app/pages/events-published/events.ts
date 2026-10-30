import { Component } from '@angular/core';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  imports: [MenuSideBarComponent, CommonModule],
  selector: 'app-events',
  styleUrl: './events.css',
  templateUrl: './events.html',
})
export class EventsComponent {
  events = [
    {
      name: 'Event 1',
      day: '12/12/2023',
      time: '10:00 AM',
      location: 'Location for Event 1'
    },
    {
      name: 'Event 2',
      day: '06/06/2023',
      time: '2:00 PM',
      location: 'Location for Event 2'
    },
    {
      name: 'Event 4',
      day: '04/04/2023',
      time: '4:00 PM',
      location: 'Location for Event 4'
    },
    {
      name: 'Event 5',
      day: '05/05/2023',
      time: '6:00 PM',
      location: 'Location for Event 5'
    }
  ];

  constructor(private router: Router) {}

  createEvent() {
    this.router.navigate(['/events/create']);
  }
}
