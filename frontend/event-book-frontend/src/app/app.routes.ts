import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { SingupComponent } from './pages/singup/singup';
import { SingupPerfilTypeComponet } from './pages/singup/singup-perfil-type/singup-perfil-type';
import { EventsComponent } from './pages/events-published/events';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'singup-perfil-type', component: SingupPerfilTypeComponet },
  { path: 'singup', component: SingupComponent },
  { path: 'events', component: EventsComponent },
];