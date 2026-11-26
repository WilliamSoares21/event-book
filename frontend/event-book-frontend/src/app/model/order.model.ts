export interface OrderItemResponse {
  id: string;
  ticketLotId: string;
  ticketLotName: string;
  quantity: number;
  unitPriceSnapshot: number;
  totalPrice: number;
}

export interface OrderResponse {
  id: string;
  items: OrderItemResponse[];
  totalPrice: number;
  status: 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
  
  // Legacy fields for backward compatibility (deprecated)
  ticketLotId?: string;
  quantity?: number;
  unitPriceSnapshot?: number;
}

export const orderStatusLabels: Record<OrderResponse['status'], string> = {
  PENDING: 'Pendente',
  PAID: 'Pago',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};

// Request DTOs for grouped orders
export interface OrderItemRequest {
  ticketLotId: string;
  quantity: number;
}

export interface OrderRequest {
  items: OrderItemRequest[];
}