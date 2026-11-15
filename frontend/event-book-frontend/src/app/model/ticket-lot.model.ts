export interface TicketLotRequest {
  name: string;
  unitPrice: number;
  capacity: number;
  salesStart: string;
  salesEnd: string;
}

export interface TicketLotResponse extends TicketLotRequest {
  id: string;
  eventId: string;
}
