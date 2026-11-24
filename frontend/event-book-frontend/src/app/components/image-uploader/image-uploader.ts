import { Component, EventEmitter, inject, Output, signal } from '@angular/core';
import { EventsService } from '../../services/events/events';
import { calculateSha256Base64 } from '../../utils/crypto';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-image-uploader',
  standalone: true,
  templateUrl: './image-uploader.html',
  styleUrls: ['./image-uploader.css']
})
export class ImageUploaderComponent {
  private readonly eventsService = inject(EventsService);
  private readonly http = inject(HttpClient);
  
  @Output() imageUploaded = new EventEmitter<string>();
  @Output() uploadError = new EventEmitter<string>();
  
  readonly uploading = signal(false);

  async onFileSelected(event: any) {
    const file = event.target.files[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      this.uploadError.emit('Arquivo muito grande (máx 5MB).');
      return;
    }
    
    this.uploading.set(true);
    try {
      const sha256 = await calculateSha256Base64(file);
      const writeId = crypto.randomUUID();
      
      // Chamar o backend para pegar URL
      // Nota: o evento ainda não foi criado na chamada inicial, 
      // precisamos passar o eventId quando ele existir.
      // Vou emitir o arquivo para o pai lidar com o fluxo de criação.
      // Esta componente será apenas um helper de upload.
    } catch (e) {
      this.uploadError.emit('Erro ao processar imagem.');
    } finally {
      this.uploading.set(false);
    }
  }
}
