import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { CardService } from '../../services/card/card';
import { CardResponseDTO } from '../../model/card.model';
import { ToastService } from '../../services/toast/toast';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-my-cards',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './my-cards.html',
  styleUrl: './my-cards.css',
})
export class MyCardsComponent {
  private readonly cardService = inject(CardService);
  private readonly toast = inject(ToastService);

  cards = signal<CardResponseDTO[]>([]);
  isLoading = signal<boolean>(true);
  isCreating = signal<boolean>(false);
  deactivatingCardId = signal<string | null>(null);

  ngOnInit() {
    this.loadCards();
  }

  loadCards() {
    this.isLoading.set(true);
    this.cardService.getMyCards().subscribe({
      next: (cards: CardResponseDTO[]) => {
        this.cards.set(cards);
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        const errObj = error.error as { message?: string };
        this.toast.show('Erro ao carregar cartões: ' + (errObj?.message || error.message));
      },
    });
  }

  createCard() {
    this.isCreating.set(true);
    this.cardService.createCard().subscribe({
      next: (card: CardResponseDTO) => {
        // Prepend to maintain order (newest first, matching API response order)
        this.cards.update((current: CardResponseDTO[]) => [card, ...current]);
        this.isCreating.set(false);
        this.toast.show('Cartão cadastrado com sucesso!');
      },
      error: (error: HttpErrorResponse) => {
        this.isCreating.set(false);
        const errObj = error.error as { message?: string };
        this.toast.show('Erro ao cadastrar cartão: ' + (errObj?.message || error.message));
      },
    });
  }

  deactivateCard(cardId: string) {
    if (!confirm('Tem certeza que deseja desativar este cartão?')) {
      return;
    }
    this.deactivatingCardId.set(cardId);
    this.cardService.deactivateCard(cardId).subscribe({
      next: () => {
        // Recarregar a lista após desativação
        this.loadCards();
        this.deactivatingCardId.set(null);
        this.toast.show('Cartão desativado com sucesso!');
      },
      error: (error: HttpErrorResponse) => {
        this.deactivatingCardId.set(null);
        const errObj = error.error as { message?: string };
        this.toast.show('Erro ao desativar cartão: ' + (errObj?.message || error.message));
      },
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'status-active';
      case 'INACTIVE':
        return 'status-inactive';
      case 'BLOCKED':
        return 'status-blocked';
      default:
        return '';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'Ativo';
      case 'INACTIVE':
        return 'Inativo';
      case 'BLOCKED':
        return 'Bloqueado';
      default:
        return status;
    }
  }

  getBrandIcon(brand: string): string {
    const upperBrand = (brand || '').toUpperCase();
    if (upperBrand.includes('VISA')) return 'bi-credit-card';
    if (upperBrand.includes('MASTER')) return 'bi-credit-card-2-front';
    if (upperBrand.includes('AMEX') || upperBrand.includes('AMERICAN')) return 'bi-credit-card-2-front';
    return 'bi-credit-card';
  }
}
