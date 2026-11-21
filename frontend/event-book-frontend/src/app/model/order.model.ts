export interface OrderResponse {
  id: string;
  ticketLotId: string;
  quantity: number;
  unitPriceSnapshot: number;
  totalPrice: number;
  status: 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
}

export const orderStatusLabels: Record<OrderResponse['status'], string> = {
  PENDING: 'Pendente',
  PAID: 'Pago',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};