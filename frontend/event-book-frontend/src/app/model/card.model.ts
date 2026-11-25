export interface CardResponseDTO {
  id: string;
  lastFourDigits: string;
  brand: string;
  expiryMonth: number;
  expiryYear: number;
  totalLimit: number; // BigDecimal no backend, mas number é ok para frontend
  availableLimit: number; // BigDecimal no backend, mas number é ok para frontend
  status: 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
  createdAt: string; // Instant no backend
  displayName: string;
  expiryDisplay: string;
  formattedTotalLimit: string;
  formattedAvailableLimit: string;
}
