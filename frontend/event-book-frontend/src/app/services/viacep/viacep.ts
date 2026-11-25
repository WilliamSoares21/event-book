import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ViaCepResponse {
  cep: string;
  logradouro: string;
  complemento: string;
  bairro: string;
  localidade: string; // cidade
  uf: string; // estado
  ibge: string;
  gia: string;
  ddd: string;
  siafi: string;
  erro?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ViaCepService {
  private readonly http = inject(HttpClient);

  getAddressByCep(cep: string): Observable<ViaCepResponse> {
    const cleanCep = cep.replace(/\D/g, ''); // Remove non-digits
    return this.http.get<ViaCepResponse>(`https://viacep.com.br/ws/${cleanCep}/json/`);
  }
}
