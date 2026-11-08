import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { SingupComponent } from './pages/singup/singup';
import { SingupPerfilTypeComponet } from './pages/singup/singup-perfil-type/singup-perfil-type';
import { EventsComponent } from './pages/events-published/events';
import { TicketsComponent } from './pages/tickets/tickets';
import { CreateEventsComponent } from './pages/events-published/create-events/create-events';
import { CreateLotsComponents } from './pages/events-published/create-lots/create-lots';
import { AcessoGuard } from './guard/AcessoGuard';
import { DetailsTickets } from './pages/tickets/details-tickets/details-tickets';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'singup-perfil-type', component: SingupPerfilTypeComponet },
  { path: 'singup', component: SingupComponent },
  { path: 'events', component: EventsComponent,canActivate: [AcessoGuard] },
  { path: 'events/create', component: CreateEventsComponent,canActivate: [AcessoGuard] },
  { path: 'events/lots/create', component: CreateLotsComponents,canActivate: [AcessoGuard] },
  { path: 'tickets', component: TicketsComponent,canActivate: [AcessoGuard] },
  { path: 'tickets/:id', component: DetailsTickets},
];