import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { CardResponseDTO } from '../../model/card.model';
import { SessionService } from '../session/session';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CardService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly apiUrl = environment.apiUrl;

  private authOptions() {
    const token = this.session.user()?.token;
    return {
      headers: new HttpHeaders({
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        'Content-Type': 'application/json',
      }),
    };
  }

  getMyCards(): Observable<CardResponseDTO[]> {
    return this.http.get<CardResponseDTO[]>(`${this.apiUrl}/cards/me`, this.authOptions());
  }

  createCard(): Observable<CardResponseDTO> {
    return this.http.post<CardResponseDTO>(`${this.apiUrl}/cards`, null, this.authOptions());
  }

  deactivateCard(cardId: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/cards/${cardId}/deactivate`, null, this.authOptions());
  }
}
