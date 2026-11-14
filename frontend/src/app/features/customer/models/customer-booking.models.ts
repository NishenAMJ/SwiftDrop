export interface AddressDto {
  contactName: string;
  contactPhone: string;
  streetAddress: string;
  city: string;
  postalCode?: string;
  zoneCode?: string;
}

export type UrgencyType = 'NORMAL' | 'URGENT';

export type ParcelStatus =
  | 'BOOKED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | string;

export interface CreateBookingRequest {
  description?: string;
  weightKg: number;
  urgency: UrgencyType;
  pickupAddress: AddressDto;
  deliveryAddress: AddressDto;
}

export interface BookingSummaryDto {
  id: string;
  trackingNumber: string;
  description: string;
  weightKg: number;
  urgency: UrgencyType;
  status: ParcelStatus;
  estimatedFee: number;
  createdAt: string;
  recipientName?: string;
  destinationCity?: string;
}

export interface BookingResponseDto {
  id: string;
  trackingNumber: string;
  customerId: string;
  description: string;
  weightKg: number;
  urgency: UrgencyType;
  status: ParcelStatus;
  estimatedFee: number;
  createdAt: string;
  receiptNumber?: string;
  pickupAddress?: AddressDto;
  deliveryAddress?: AddressDto;
}

export interface BookingReceiptDto {
  receiptNumber: string;
  parcelId: string;
  trackingNumber: string;
  issuedAt: string;
  totalAmount: number;
  currency: string;
  description?: string;
  weightKg?: number;
  urgency?: UrgencyType;
  status?: ParcelStatus;
  pickupAddress?: AddressDto;
  deliveryAddress?: AddressDto;
}

export interface FeeBreakdown {
  baseFee: number;
  weightFee: number;
  urgencySurcharge: number;
  totalFee: number;
}

