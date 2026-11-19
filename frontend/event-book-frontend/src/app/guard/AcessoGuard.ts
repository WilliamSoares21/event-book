import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session/session';

export const AcessoGuard: CanActivateFn = (route) => {
  const role = inject(SessionService).user()?.role;
  const allowedRoles: string[] = route.data?.['allowedRoles'] ?? ['ORGANIZADOR', 'COMPRADOR'];

  if (role && allowedRoles.includes(role)) {
    return true;
  }

  return inject(Router).createUrlTree(['/login'], {
    queryParams: { mensagem: 'Acesso negado. Você não tem permissão para acessar esta página.' },
  });
};
