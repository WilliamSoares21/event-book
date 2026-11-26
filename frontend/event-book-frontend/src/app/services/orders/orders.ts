import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { SessionService } from '../session/session';
import { OrderResponse, orderStatusLabels } from '../../model/order.model';

export { orderStatusLabels } from '../../model/order.model';

@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly apiUrl = environment.apiUrl;

  listOwnOrders() {
    const token = this.session.user()?.token;
    return this.http.get<OrderResponse[]>(`${this.apiUrl}/orders/me`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  }

  cancelOrder(orderId: string) {
    const token = this.session.user()?.token;
    return this.http.delete<void>(
      `${this.apiUrl}/orders/${encodeURIComponent(orderId)}`,
      { headers: token ? { Authorization: `Bearer ${token}` } : {} }
    );
  }
}