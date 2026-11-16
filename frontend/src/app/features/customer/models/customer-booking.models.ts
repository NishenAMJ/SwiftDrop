export interface AddressDto {
  contactName: string;
  contactPhone: string;
  streetAddress: string;
  city: string;
  postalCode?: string;
  zoneCode?: string;
}

export type UrgencyType = 'NORMAL' | 'URGENT';
export type PaymentMethod = 'ONLINE' | 'COD';

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
  paymentMethod?: PaymentMethod;
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
  paymentMethod?: PaymentMethod;
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
  paymentMethod?: PaymentMethod;
  pickupAddress?: AddressDto;
  deliveryAddress?: AddressDto;
}

export interface FeeBreakdown {
  baseFee: number;
  weightFee: number;
  urgencySurcharge: number;
  totalFee: number;
}

export interface PaymentContextResponse {
  bookingId: string;
  channel: string;
  customerId: string;
  merchantId?: string | null;
  payable: boolean;
  pricingSnapshotId: string;
  amount: number;
  currency: string;
  payer: {
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
    address: string;
    city: string;
    country: string;
  };
}

export interface CancellationEligibility {
 eligible: boolean;
 reason: string | null;
 booking: BookingResponseDto | null;
 scheduledPickupTime: string | null;
 cancellationFee: number | null;
 originalPaidAmount: number | null;
 refundableAmount: number | null;
 paymentStatus: string | null;
}
export interface CancellationInitiation {
 cancellationStatus?: string;
 bookingStatus?: string;
 completed?: boolean;
}

export interface BookingCheckout {
 id: string;
 status: string;
 checkout?: { provider: string; checkoutUrl: string; method: string; fields: Record<string,string> };
}
