import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { SingupComponent } from './pages/singup/singup';
import { SingupPerfilTypeComponet } from './pages/singup/singup-perfil-type/singup-perfil-type';
import { ShopEvents } from './pages/events-published/shop-events/shop-events';
import { EventsComponent } from './pages/events-published/events';
import { Checkin } from './pages/checkin/checkin';
import { StaffEvents } from './pages/staff/staff-events/staff-events';
import { StaffColaborador } from './pages/staff/staff-colaborador/staff-colaborador';
import { DetailsEvents } from './pages/events-published/details-events/details-events';
import { TicketsComponent } from './pages/tickets/tickets';
import { CreateEventsComponent } from './pages/events-published/create-events/create-events';
import { CreateLotsComponents } from './pages/events-published/create-lots/create-lots';
import { AcessoGuard } from './guard/AcessoGuard';
import { DetailsTickets } from './pages/tickets/details-tickets/details-tickets';
import { OrdersComponent } from './pages/orders/orders';
import { LandingComponent } from './pages/landing/landing/landing';
import { AboutComponent } from './pages/about/about/about';
import { ContactComponent } from './pages/contact/contact/contact';

export const routes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'login', component: LoginComponent },
  { path: 'about', component: AboutComponent },
  { path: 'contact', component: ContactComponent },
  { path: 'singup-perfil-type', component: SingupPerfilTypeComponet },
  { path: 'singup', component: SingupComponent },
  { path: 'shop-events', component: ShopEvents },
  { path: 'shop-events/:id', component: DetailsEvents, data: { publicCatalog: true } },
  { path: 'staff/events/:id/check-in', component: Checkin, canActivate: [AcessoGuard], data: { allowedRoles: ['STAFF'] } },
  { path: 'staff/events', component: StaffEvents, canActivate: [AcessoGuard], data: { allowedRoles: ['STAFF'] } },
  { path: 'events', component: EventsComponent, canActivate: [AcessoGuard] },
  { path: 'events/create', component: CreateEventsComponent, canActivate: [AcessoGuard] },
  { path: 'events/lots/create', component: CreateLotsComponents, canActivate: [AcessoGuard] },
  { path: 'events/:id/staff', component: StaffColaborador, canActivate: [AcessoGuard] },
  { path: 'events/:id', component: DetailsEvents, canActivate: [AcessoGuard] },
  { path: 'tickets', component: TicketsComponent, canActivate: [AcessoGuard] },
  { path: 'tickets/:id', component: DetailsTickets, canActivate: [AcessoGuard] },
  { path: 'orders', component: OrdersComponent, canActivate: [AcessoGuard], data: { allowedRoles: ['COMPRADOR'] } },
];
