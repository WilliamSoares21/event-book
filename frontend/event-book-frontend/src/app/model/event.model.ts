export interface Event {
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  location: string;
}

export type EventListItem = Pick<EventResponse, 'id' | 'title' | 'startsAt' | 'location'>;

export interface EventResponse extends Event {
  id: string;
  organizerId: string;
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'FINISHED';
  version: number;
  availableTicketLots?: AvailableTicketLot[];
}

export interface AvailableTicketLot {
  id: string;
  name: string;
  unitPrice: number;
  availableQuantity: number;
}
