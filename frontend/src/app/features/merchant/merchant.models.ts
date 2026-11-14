/** Shapes from contracts/openapi/identity-merchant-service.yaml (Merchants, API keys). */
import { ApplicationStatus, StatusDecision } from '../../shared/models/application';

export type BusinessType = 'INDIVIDUAL' | 'SOLE_PROPRIETORSHIP' | 'PARTNERSHIP' | 'COMPANY';

export const BUSINESS_TYPE_LABELS: Record<BusinessType, string> = {
  INDIVIDUAL: 'Individual',
  SOLE_PROPRIETORSHIP: 'Sole proprietorship',
  PARTNERSHIP: 'Partnership',
  COMPANY: 'Company',
};

/** Business types that must give a registration number. */
export const REGISTERED_BUSINESS_TYPES: readonly BusinessType[] = ['PARTNERSHIP', 'COMPANY'];

export interface Address {
  line1: string;
  line2?: string | null;
  city: string;
  postalCode?: string | null;
}

/** One line, for display: "12 Galle Road, Floor 2, Colombo 00300". */
export function formatAddress(address: Address): string {
  const city = [address.city, address.postalCode].filter(Boolean).join(' ');
  return [address.line1, address.line2, city].filter(Boolean).join(', ');
}

export interface Merchant {
  id: string;
  userId: string;
  businessName: string;
  businessType: BusinessType;
  businessRegistrationNumber?: string | null;
  contactEmail?: string | null;
  contactPhone: string;
  businessAddress: Address;
  status: ApplicationStatus;
  lastDecision?: StatusDecision | null;
  submittedAt: string;
  createdAt: string;
}

export interface MerchantApplicationRequest {
  businessName: string;
  businessType: BusinessType;
  businessRegistrationNumber?: string;
  contactEmail?: string;
  contactPhone: string;
  businessAddress: Address;
}

export interface ApiKey {
  id: string;
  name: string;
  /** First characters of the key, so the merchant can recognise it. Never the secret. */
  prefix: string;
  createdAt: string;
  lastUsedAt?: string | null;
  expiresAt?: string | null;
  revokedAt?: string | null;
}

/** Only the create response contains the full key, and only once. */
export interface CreatedApiKey extends ApiKey {
  key: string;
}

export interface CreateApiKeyRequest {
  name: string;
  expiresAt?: string;
}

/** Most active keys a merchant can have at once (enforced by identity). */
export const MAX_ACTIVE_API_KEYS = 5;
