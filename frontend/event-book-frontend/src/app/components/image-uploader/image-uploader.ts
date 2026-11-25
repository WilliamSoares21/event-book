import { Component, EventEmitter, inject, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventsService } from '../../services/events/events';
import { calculateSha256Base64 } from '../../utils/crypto';
import { ToastService } from '../../services/toast/toast';
import { HttpClient } from '@angular/common/http';

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
  private readonly http = inject(HttpClient);

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
    const file = event.target.files[0];
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
    await this.uploadFile(file, this.eventId);
  }

  async uploadFile(file: File, eventId: string) {
    this.uploading.set(true);
    try {
      const sha256Base64 = await calculateSha256Base64(file);
      const writeId = crypto.randomUUID();
      const sizeBytes = file.size;
      const contentType = file.type;

      // 1. Get pre-signed URL
      this.eventsService.getUploadUrl(eventId, {
        contentType,
        sha256Base64,
        writeId,
        sizeBytes
      }).subscribe({
        next: (urlRes) => {
          // 2. Direct PUT to R2 pre-signed URL using HttpClient (not Angular EventsService)
          this.http.put(urlRes.uploadUrl, file, {
            headers: { 'Content-Type': contentType }
          }).subscribe({
            next: () => {
              // 3. Confirm upload
              this.eventsService.confirmUploadImage(eventId, {
                objectKey: urlRes.objectKey,
                writeId,
                sha256Base64,
                sizeBytes
              }).subscribe({
                next: () => {
                  this.uploading.set(false);
                  this.previewUrl.set(urlRes.publicUrl);
                  this.imageUploaded.emit(urlRes.publicUrl);
                  this.toast.show('Imagem de capa enviada com sucesso!');
                },
                error: (err) => {
                  this.uploading.set(false);
                  this.toast.show('Erro ao confirmar upload da imagem.');
                  console.error(err);
                }
              });
            },
            error: (err) => {
              this.uploading.set(false);
              this.toast.show('Erro ao enviar arquivo para o armazenamento.');
              console.error(err);
            }
          });
        },
        error: (err) => {
          this.uploading.set(false);
          this.toast.show('Erro ao obter URL de upload.');
          console.error(err);
        }
      });

    } catch (e) {
      this.uploading.set(false);
      this.toast.show('Erro ao processar imagem.');
      console.error(e);
    }
  }

  removeImage() {
    if (this.eventId) {
      this.eventsService.removeEventImage(this.eventId).subscribe({
        next: () => {
          this.previewUrl.set(null);
          this.imageRemoved.emit();
          this.toast.show('Imagem de capa removida.');
        },
        error: (err) => {
          this.toast.show('Erro ao remover imagem.');
          console.error(err);
        }
      });
    } else {
      this.previewUrl.set(null);
      this.imageRemoved.emit();
    }
  }
}
