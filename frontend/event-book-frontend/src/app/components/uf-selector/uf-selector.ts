import { Component, signal, effect, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

export interface UF {
  sigla: string;
  nome: string;
}

@Component({
  selector: 'app-uf-selector',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="uf-selector">
      <label [for]="inputId" class="form-label">
        {{ label }}
        @if (required) {
          <span class="text-danger" aria-hidden="true">*</span>
        }
      </label>
      <div class="position-relative">
        <select
          [id]="inputId"
          class="form-select"
          [formControl]="control"
          [disabled]="disabled"
          [attr.aria-describedby]="errorMessage() ? (inputId + '-error') : null"
          [attr.aria-invalid]="!!errorMessage()"
        >
          <option value="">Selecione a UF</option>
          @for (uf of filteredUfs(); track uf.sigla) {
            <option [value]="uf.sigla">{{ uf.sigla }} - {{ uf.nome }}</option>
          }
        </select>
        @if (errorMessage()) {
          <div class="form-text text-danger" [id]="inputId + '-error'" role="alert">
            {{ errorMessage() }}
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .uf-selector {
      width: 100%;
    }
    .form-select {
      min-height: calc(1.5em + 0.75rem + 2px);
    }
  `]
})
export class UfSelectorComponent {
  // Inputs
  @Input({ required: true }) control!: FormControl<string | null>;
  @Input() label: string = 'UF';
  @Input() required: boolean = true;
  @Input() disabled: boolean = false;
  @Input() inputId: string = 'uf-selector';
  @Input() placeholder: string = 'Selecione a UF';
  
  // Output
  @Output() selectionChange = new EventEmitter<string>();
  
  // Internal state
  errorMessage = signal<string>('');
  filteredUfs = signal<UF[]>([]);
  
  // All 27 Brazilian states
  private readonly allUfs: UF[] = [
    { sigla: 'AC', nome: 'Acre' },
    { sigla: 'AL', nome: 'Alagoas' },
    { sigla: 'AP', nome: 'Amapá' },
    { sigla: 'AM', nome: 'Amazonas' },
    { sigla: 'BA', nome: 'Bahia' },
    { sigla: 'CE', nome: 'Ceará' },
    { sigla: 'DF', nome: 'Distrito Federal' },
    { sigla: 'ES', nome: 'Espírito Santo' },
    { sigla: 'GO', nome: 'Goiás' },
    { sigla: 'MA', nome: 'Maranhão' },
    { sigla: 'MT', nome: 'Mato Grosso' },
    { sigla: 'MS', nome: 'Mato Grosso do Sul' },
    { sigla: 'MG', nome: 'Minas Gerais' },
    { sigla: 'PA', nome: 'Pará' },
    { sigla: 'PB', nome: 'Paraíba' },
    { sigla: 'PR', nome: 'Paraná' },
    { sigla: 'PE', nome: 'Pernambuco' },
    { sigla: 'PI', nome: 'Piauí' },
    { sigla: 'RJ', nome: 'Rio de Janeiro' },
    { sigla: 'RN', nome: 'Rio Grande do Norte' },
    { sigla: 'RS', nome: 'Rio Grande do Sul' },
    { sigla: 'RO', nome: 'Rondônia' },
    { sigla: 'RR', nome: 'Roraima' },
    { sigla: 'SC', nome: 'Santa Catarina' },
    { sigla: 'SP', nome: 'São Paulo' },
    { sigla: 'SE', nome: 'Sergipe' },
    { sigla: 'TO', nome: 'Tocantins' },
  ];

  constructor() {
    // Initialize with all UFs
    this.filteredUfs.set(this.allUfs);
    
    // Subscribe to control value changes to validate
    effect(() => {
      const value = this.control.value;
      if (value) {
        const upperValue = value.toUpperCase();
        const isValid = this.allUfs.some(uf => uf.sigla === upperValue);
        if (!isValid) {
          this.errorMessage.set('UF inválida. Selecione uma das 27 unidades federativas.');
        } else {
          this.errorMessage.set('');
          this.selectionChange.emit(upperValue);
        }
      } else if (this.required) {
        this.errorMessage.set('UF é obrigatória.');
      } else {
        this.errorMessage.set('');
      }
    });
  }
  
  // Method to find UF by sigla or nome
  findUf(query: string): UF | undefined {
    const upperQuery = query.toUpperCase();
    return this.allUfs.find(uf => 
      uf.sigla === upperQuery || 
      uf.nome.toUpperCase().includes(upperQuery)
    );
  }
}