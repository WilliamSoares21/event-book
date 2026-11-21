import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import { SessionService } from '../../services/session/session';
import { OrdersService } from '../../services/orders/orders';
import { OrderResponse, orderStatusLabels } from '../../model/order.model';

@Component({
  imports: [MenuSideBarComponent, CurrencyPipe, DatePipe],
  selector: 'app-orders',
  styleUrl: './orders.css',
  templateUrl: './orders.html',
})
export class OrdersComponent implements OnInit {
  private readonly session = inject(SessionService);
  private readonly ordersService = inject(OrdersService);
  private readonly destroyRef = inject(DestroyRef);

  readonly orders = signal<OrderResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly cancelling = signal<Set<string>>(new Set());
  readonly statusLabels = orderStatusLabels;

  get roleUser() { return this.session.user()?.role; }

  ngOnInit() {
    this.loadOrders();
  }

  private loadOrders() {
    if (this.roleUser !== 'COMPRADOR') {
      this.error.set('A listagem de pedidos é exclusiva para compradores.');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.ordersService.listOwnOrders().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.error.set('Sessão expirada. Faça login novamente.');
        } else if (err.status === 503) {
          this.error.set('Serviço indisponível. Tente novamente mais tarde.');
        } else {
          this.error.set('Não foi possível carregar seus pedidos.');
        }
      },
    });
  }

  canCancel(order: OrderResponse): boolean {
    return order.status === 'PENDING' || order.status === 'PAID';
  }

  async onCancel(orderId: string) {
    if (this.cancelling().has(orderId)) return;

    const confirmed = window.confirm(
      'Tem certeza que deseja cancelar este pedido? Esta ação não pode ser desfeita.'
    );
    if (!confirmed) return;

    const newCancelling = new Set(this.cancelling());
    newCancelling.add(orderId);
    this.cancelling.set(newCancelling);

    this.ordersService.cancelOrder(orderId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        const updatedCancelling = new Set(this.cancelling());
        updatedCancelling.delete(orderId);
        this.cancelling.set(updatedCancelling);

        this.loadOrders();
        alert('Pedido cancelado com sucesso.');
      },
      error: (err) => {
        const updatedCancelling = new Set(this.cancelling());
        updatedCancelling.delete(orderId);
        this.cancelling.set(updatedCancelling);

        let message = 'Não foi possível cancelar o pedido.';
        if (err.status === 401) {
          message = 'Sessão expirada. Faça login novamente.';
        } else if (err.status === 403) {
          message = 'Você não tem permissão para cancelar este pedido.';
        } else if (err.status === 404) {
          message = 'Pedido não encontrado.';
        } else if (err.status === 409) {
          message = 'Este pedido já foi cancelado.';
        } else if (err.status === 422) {
          message = 'Cancelamento não permitido: o pedido está expirado ou possui ingresso com check-in realizado.';
        } else if (err.status === 0) {
          message = 'Erro de conexão. Verifique sua internet e tente novamente.';
        }
        alert(message);
      },
    });
  }

  isCancelling(orderId: string): boolean {
    return this.cancelling().has(orderId);
  }
}