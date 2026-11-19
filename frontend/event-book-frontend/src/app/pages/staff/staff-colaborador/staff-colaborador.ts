import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, switchMap } from 'rxjs';
import { EventStaffOption, EventsService } from '../../../services/events/events';

@Component({
  imports: [RouterLink],
  selector: 'app-staff-colaborador',
  styleUrl: './staff-colaborador.css',
  templateUrl: './staff-colaborador.html',
})
export class StaffColaborador implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly eventsService = inject(EventsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly eventId = signal('');
  readonly staff = signal<EventStaffOption[] | null>(null);
  readonly loadError = signal('');
  readonly saving = signal<string | null>(null);
  readonly feedback = signal<{ message: string; error: boolean } | null>(null);

  ngOnInit() {
    this.route.paramMap.pipe(
      switchMap((params) => {
        const eventId = params.get('id')!;
        this.eventId.set(eventId);
        this.staff.set(null);
        this.loadError.set('');
        this.saving.set(null);
        this.feedback.set(null);

        return this.eventsService.listStaffForEvent(eventId).pipe(
          catchError((error) => {
            this.loadError.set(error.error?.error ?? 'Não foi possível carregar os colaboradores.');
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((staff) => this.staff.set(staff));
  }

  associate(staff: EventStaffOption) {
    if (staff.associated || this.saving()) return;

    const eventId = this.eventId();
    this.saving.set(staff.id);
    this.feedback.set(null);
    this.eventsService.addStaffToEvent(eventId, staff.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.finishAssociation(eventId, staff, 'Colaborador associado ao evento.'),
        error: (error) => {
          if (error.status === 409) {
            this.finishAssociation(eventId, staff, 'Este colaborador já está associado ao evento.');
          } else if (this.eventId() === eventId) {
            this.saving.set(null);
            this.feedback.set({
              message: error.error?.error ?? 'Não foi possível associar o colaborador.',
              error: true,
            });
          }
        },
      });
  }

  private finishAssociation(eventId: string, staff: EventStaffOption, message: string) {
    if (this.eventId() !== eventId) return;

    this.staff.update((entries) => entries?.map((entry) =>
      entry.id === staff.id ? { ...entry, associated: true } : entry,
    ) ?? null);
    this.saving.set(null);
    this.feedback.set({ message, error: false });
  }
}
