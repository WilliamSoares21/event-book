export interface Event {
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  location: string;
}

export type EventListItem = Pick<Event, 'title' | 'startsAt' | 'location'>;
