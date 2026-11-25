export interface Event {
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  venueName: string;
  street: string;
  number?: string;
  complement?: string;
  neighborhood: string;
  city: string;
  state: string;
  zipCode?: string;
}

export type EventListItem = Pick<EventResponse, 'id' | 'title' | 'startsAt' | 'location' | 'coverImageUrl'>;

export interface EventResponse extends Event {
  id: string;
  organizerId: string;
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'FINISHED';
  version: number;
  location: string;
  coverImageUrl?: string;
  availableTicketLots?: AvailableTicketLot[];
}

export interface AvailableTicketLot {
  id: string;
  name: string;
  unitPrice: number;
  availableQuantity: number;
}
