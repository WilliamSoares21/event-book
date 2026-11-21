import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { LoginRequest } from '../../model/LoginRequest';
import { LoginResponse } from '../../model/LoginResponse';

export interface LoginError {
  status: number;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class LoginService {
  constructor(private readonly http: HttpClient) {}

  register(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/users`, credentials);
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, credentials)
      .pipe(catchError((error: HttpErrorResponse) => throwError(() => this.mapError(error))));
  }

  logout(token: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/logout`, null, {
      headers: { Authorization: `Bearer ${token}` },
      timeout: 5000,
    });
  }

  private mapError(error: HttpErrorResponse): LoginError {
    if (error.status === 0) {
      return { status: 0, message: 'Não foi possível conectar ao servidor. Verifique sua conexão.' };
    }
    if (error.status === 400) {
      const fieldError = error.error?.fields?.password ?? error.error?.fields?.email ?? error.error?.error;
      return { status: 400, message: typeof fieldError === 'string' ? fieldError : 'Dados de login inválidos.' };
    }
    if (error.status === 401) {
      return { status: 401, message: 'E-mail ou senha inválidos.' };
    }
    if (error.status === 403) {
      return { status: 403, message: 'Acesso não autorizado.' };
    }
    if (error.status >= 500) {
      return { status: error.status, message: 'Erro interno do servidor. Tente novamente mais tarde.' };
    }
    return { status: error.status, message: 'Erro inesperado. Tente novamente.' };
  }
}
