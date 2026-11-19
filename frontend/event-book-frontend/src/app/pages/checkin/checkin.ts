import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, map, switchMap } from 'rxjs';
import { MenuSideBarComponent } from '../../components/menu-side-bar/menu-side-bar';
import type { EventListItem } from '../../model/event.model';
import { EventsService } from '../../services/events/events';
import { SessionService } from '../../services/session/session';

@Component({
  imports: [DatePipe, MenuSideBarComponent, RouterLink],
  selector: 'app-checkin',
  styleUrl: './checkin.css',
  templateUrl: './checkin.html',
})
export class Checkin implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly eventsService = inject(EventsService);
  private readonly destroyRef = inject(DestroyRef);
  readonly session = inject(SessionService);

  readonly event = signal<EventListItem | null>(null);
  readonly loadError = signal('');
  readonly submitting = signal(false);
  readonly feedback = signal<{ message: string; error: boolean; checkedInAt?: string } | null>(null);

  ngOnInit() {
    this.route.paramMap.pipe(
      switchMap((params) => {
        this.event.set(null);
        this.loadError.set('');
        this.submitting.set(false);
        this.feedback.set(null);

        return this.eventsService.listAssignedStaffEvents().pipe(
          map((events) => events.find((event) => event.id === params.get('id'))),
          catchError(() => {
            this.loadError.set('Não foi possível carregar o evento.');
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((event) => {
      if (event) {
        this.event.set(event);
      } else {
        this.loadError.set('Evento não vinculado à sua conta de staff.');
      }
    });
  }

  checkIn(input: HTMLInputElement) {
    const event = this.event();
    if (!event || this.submitting()) return;

    const code = input.value.trim();
    if (!code || code.length > 64) {
      this.feedback.set({ message: 'Informe um código de ingresso de até 64 caracteres.', error: true });
      input.focus();
      return;
    }

    this.submitting.set(true);
    this.feedback.set(null);
    this.eventsService.checkInTicket(code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (this.event() !== event) return;

          this.submitting.set(false);
          this.feedback.set({ message: 'Check-in realizado com sucesso.', error: false, checkedInAt: result.checkedInAt });
          input.value = '';
          input.focus();
        },
        error: (error) => {
          if (this.event() !== event) return;

          this.submitting.set(false);
          this.feedback.set(error.status === 409
            ? { message: 'Este ingresso já possui check-in.', error: true, checkedInAt: error.error?.checkedInAt }
            : { message: error.error?.error ?? 'Não foi possível realizar o check-in.', error: true });
          input.focus();
          input.select();
        },
      });
  }
}
