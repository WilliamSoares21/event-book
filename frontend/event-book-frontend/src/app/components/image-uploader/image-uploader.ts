import { Component, EventEmitter, inject, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventsService, ImageUploadError } from '../../services/events/events';
import { ToastService } from '../../services/toast/toast';

@Component({
  selector: 'app-image-uploader',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './image-uploader.html',
  styleUrls: ['./image-uploader.css']
})
export class ImageUploaderComponent {
  private readonly eventsService = inject(EventsService);
  private readonly toast = inject(ToastService);

  @Input() eventId?: string;
  @Input() currentCoverUrl?: string;
  @Output() imageUploaded = new EventEmitter<string>();
  @Output() imageRemoved = new EventEmitter<void>();
  @Output() fileSelectedForLater = new EventEmitter<File>();

  readonly uploading = signal(false);
  readonly previewUrl = signal<string | null>(null);

  ngOnInit() {
    if (this.currentCoverUrl) {
      this.previewUrl.set(this.currentCoverUrl);
    }
  }

  async onFileSelected(event: any) {
    const file: File | undefined = event.target.files[0];
    if (!file) return;

    // Validate type
    const validTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (!validTypes.includes(file.type)) {
      this.toast.show('Formato inválido. Utilize JPEG, PNG ou WebP.');
      return;
    }

    // Validate size (5MB)
    if (file.size > 5 * 1024 * 1024) {
      this.toast.show('Arquivo muito grande. O limite é 5 MB.');
      return;
    }

    // Generate local preview
    const reader = new FileReader();
    reader.onload = (e: any) => {
      this.previewUrl.set(e.target.result);
    };
    reader.readAsDataURL(file);

    // If eventId is not yet created, emit file for parent component to handle after event creation
    if (!this.eventId) {
      this.fileSelectedForLater.emit(file);
      return;
    }

    // Otherwise, execute R2 upload flow immediately
    this.uploadFile(file, this.eventId);
  }

  uploadFile(file: File, eventId: string) {
    this.uploading.set(true);
    this.eventsService.uploadEventImage(eventId, file).subscribe({
      next: (publicUrl) => {
        this.uploading.set(false);
        this.previewUrl.set(publicUrl);
        this.imageUploaded.emit(publicUrl);
        this.toast.show('Imagem de capa enviada com sucesso!');
      },
      error: (error) => {
        this.uploading.set(false);
        this.toast.show(error instanceof ImageUploadError ? error.message : 'Erro ao processar imagem.');
        this.logUploadError(error);
      }
    });
  }

  removeImage() {
    if (this.eventId) {
      this.eventsService.removeEventImage(this.eventId).subscribe({
        next: () => {
          this.previewUrl.set(null);
          this.imageRemoved.emit();
          this.toast.show('Imagem de capa removida.');
        },
        error: (error) => {
          this.toast.show('Erro ao remover imagem.');
          this.logUploadError(error);
        }
      });
    } else {
      this.previewUrl.set(null);
      this.imageRemoved.emit();
    }
  }

  // Loga apenas a etapa/status: o HttpErrorResponse carrega a URL pré-assinada completa.
  private logUploadError(error: unknown) {
    if (error instanceof ImageUploadError) {
      console.error('Falha no upload da imagem', { stage: error.stage, status: error.status });
    } else {
      console.error('Falha ao processar a imagem', error instanceof Error ? error.message : error);
    }
  }
}
