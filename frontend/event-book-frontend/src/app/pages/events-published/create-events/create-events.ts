import { Component, inject, signal, effect, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormControl } from '@angular/forms';
import { ToastService } from '../../../services/toast/toast';
import { ViaCepService, ViaCepResponse } from '../../../services/viacep/viacep';
import { UfSelectorComponent } from '../../../components/uf-selector/uf-selector';
import { ImageUploaderComponent } from '../../../components/image-uploader/image-uploader';

@Component({
  imports: [ReactiveFormsModule, UfSelectorComponent, ImageUploaderComponent],
  selector: 'app-create-events',
  styleUrl: './create-events.css',
  templateUrl: './create-events.html',
})
export class CreateEventsComponent {
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  private readonly viaCepService = inject(ViaCepService);
  readonly router = inject(Router);
  
  @ViewChild(ImageUploaderComponent) imageUploader!: ImageUploaderComponent;
  
  singUpForm: FormGroup;
  
  // State for CEP lookup
  isLoadingCep = signal(false);
  cepError = signal<string>('');
  isCepFieldTouched = signal(false);
  
  // State controls for UF selector
  stateControl = new FormControl<string>('', { 
    nonNullable: true, 
    validators: [Validators.required, Validators.pattern(/^[A-Z]{2}$/)] 
  });
  
  zipCodeControl = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.pattern(/^\d{5}-?\d{3}$/)]
  });

  // Pending file to upload after event creation
  private pendingCoverFile: File | null = null;

  constructor() {
    this.singUpForm = this.fb.group({
      eventName: ['', Validators.required],
      eventDescription: [''],
      eventStartedDate: ['', Validators.required],
      eventStartedTime: ['', Validators.required],
      eventEndDate: ['', Validators.required],
      eventEndTime: ['', Validators.required],
      venueName: ['', Validators.required],
      street: ['', Validators.required],
      number: [''],
      complement: [''],
      neighborhood: ['', Validators.required],
      city: ['', Validators.required],
      state: this.stateControl,
      zipCode: this.zipCodeControl
    });
    
    // Effect to watch for CEP changes and trigger ViaCEP lookup
    effect(() => {
      const zipCode = this.zipCodeControl.value;
      const cleanCep = zipCode.replace(/\D/g, '');
      
      // Only lookup if CEP has 8 digits and field was touched
      if (cleanCep.length === 8 && this.isCepFieldTouched()) {
        this.lookupCep(cleanCep);
      }
    });
  }

  private toISOLocal(dateStr: string, timeStr: string): string {
    const d = new Date(dateStr + 'T' + timeStr);
    return d.toISOString();
  }

  onCepBlur() {
    this.isCepFieldTouched.set(true);
    const zipCode = this.zipCodeControl.value;
    const cleanCep = zipCode.replace(/\D/g, '');
    
    if (cleanCep.length === 8) {
      this.lookupCep(cleanCep);
    } else if (zipCode && cleanCep.length > 0) {
      this.cepError.set('CEP deve ter 8 dígitos (formato: 00000-000 ou 00000000)');
    }
  }

  onCepInput(event: Event) {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    
    // Format as 00000-000
    if (value.length > 5) {
      value = value.slice(0, 5) + '-' + value.slice(5, 8);
    }
    
    this.zipCodeControl.setValue(value, { emitEvent: true });
  }

  private lookupCep(cep: string) {
    this.isLoadingCep.set(true);
    this.cepError.set('');
    
    this.viaCepService.getAddressByCep(cep).subscribe({
      next: (response: ViaCepResponse) => {
        this.isLoadingCep.set(false);
        
        if (response.erro) {
          this.cepError.set('CEP não encontrado');
          return;
        }
        
        // Fill address fields with ViaCEP data
        // Only fill if field is empty or user hasn't manually edited
        if (!this.singUpForm.get('street')?.value) {
          this.singUpForm.get('street')?.setValue(response.logradouro);
        }
        if (!this.singUpForm.get('neighborhood')?.value) {
          this.singUpForm.get('neighborhood')?.setValue(response.bairro);
        }
        if (!this.singUpForm.get('city')?.value) {
          this.singUpForm.get('city')?.setValue(response.localidade);
        }
        // Always update UF from CEP as it's authoritative
        this.stateControl.setValue(response.uf.toUpperCase());
      },
      error: (error) => {
        this.isLoadingCep.set(false);
        this.cepError.set('Erro ao consultar CEP. Verifique sua conexão.');
        console.error('ViaCEP error:', error);
      }
    });
  }

  onFileSelectedForLater(file: File) {
    this.pendingCoverFile = file;
  }

  async createLots() {
    if (this.singUpForm.invalid) {
      this.toast.show('Preencha todos os campos obrigatórios corretamente.');
      Object.keys(this.singUpForm.controls).forEach(key => {
        this.singUpForm.get(key)?.markAsTouched();
      });
      return;
    }

    const { eventStartedDate, eventStartedTime, eventEndDate, eventEndTime } = this.singUpForm.value;
    const startsAt = this.toISOLocal(eventStartedDate, eventStartedTime);
    const endsAt = this.toISOLocal(eventEndDate, eventEndTime);

    if (new Date(endsAt) <= new Date(startsAt)) {
      this.toast.show('Data/hora de término deve ser posterior à de início.');
      return;
    }

    const event = {
      title: this.singUpForm.get('eventName')?.value,
      description: this.singUpForm.get('eventDescription')?.value,
      startsAt: startsAt,
      endsAt: endsAt,
      venueName: this.singUpForm.get('venueName')?.value,
      street: this.singUpForm.get('street')?.value,
      number: this.singUpForm.get('number')?.value || null,
      complement: this.singUpForm.get('complement')?.value || null,
      neighborhood: this.singUpForm.get('neighborhood')?.value,
      city: this.singUpForm.get('city')?.value,
      state: this.stateControl.value.toUpperCase(),
      zipCode: this.zipCodeControl.value.replace(/\D/g, '') || null
    };

    // Navigate to lots creation, passing both event and pending cover file
    this.router.navigate(['/events/lots/create'], { 
      state: { 
        event, 
        pendingCoverFile: this.pendingCoverFile 
      } 
    });
  }
}
