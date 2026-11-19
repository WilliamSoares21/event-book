import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: 'login', renderMode: RenderMode.Client },
  { path: 'shop-events', renderMode: RenderMode.Client },
  { path: 'shop-events/:id', renderMode: RenderMode.Client },
  { path: 'staff/events/:id/check-in', renderMode: RenderMode.Client },
  { path: 'staff/events', renderMode: RenderMode.Client },
  { path: 'events', renderMode: RenderMode.Client },
  { path: 'events/**', renderMode: RenderMode.Client },
  { path: 'tickets', renderMode: RenderMode.Client },
  { path: 'tickets/:id', renderMode: RenderMode.Client },
  {
    path: '**',
    renderMode: RenderMode.Prerender
  }
];
