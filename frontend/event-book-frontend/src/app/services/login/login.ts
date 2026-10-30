import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest } from '../../model/LoginRequest';
import { LoginResponse } from '../../model/LoginResponse';

@Injectable({ providedIn: 'root' })
export class LoginService {
  constructor(private readonly http: HttpClient) {}

  register(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/users`, credentials);
  }
}
