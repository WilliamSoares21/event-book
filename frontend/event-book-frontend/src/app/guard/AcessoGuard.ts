import { inject } from "@angular/core";
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session/session';

export const AcessoGuard: CanActivateFn = () => {
    const router = inject(Router);
    const dadosUsuario = inject(SessionService).user();
    const role = dadosUsuario?.role;

    if (role === 'ORGANIZADOR') {
        return true;
    } else if(role === 'COMPRADOR') {
        return true;
    } else {
        return router.createUrlTree(['/login'], { queryParams: { mensagem: 'Acesso negado. Você não tem permissão para acessar esta página.' } });
    }
    
}
