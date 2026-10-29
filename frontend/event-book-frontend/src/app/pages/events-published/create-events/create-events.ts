import { Component } from '@angular/core';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { Router, RouterLink } from '@angular/router';

@Component({
  imports: [MenuSideBarComponent, RouterLink],
  selector: 'app-create-events',
  styleUrl: './create-events.css',
  templateUrl: './create-events.html',
})
export class CreateEventsComponent {

  constructor(private router: Router) {}

  createLots() {
    this.router.navigate(['/events/lots/create']);
  }
}
