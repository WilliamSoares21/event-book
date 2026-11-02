import { Component, inject } from '@angular/core';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';
import { Router, RouterLink } from '@angular/router';
import { SessionService } from '../../../services/session/session';

@Component({
  imports: [MenuSideBarComponent, RouterLink],
  selector: 'app-create-events',
  styleUrl: './create-events.css',
  templateUrl: './create-events.html',
})
export class CreateEventsComponent {
  private readonly session = inject(SessionService);
  get roleUser() { return this.session.user()?.role; }
  
  constructor(private router: Router) {}

  createLots() {
    this.router.navigate(['/events/lots/create']);
  }
}
