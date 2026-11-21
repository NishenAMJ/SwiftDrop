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
  | 'CHECKOUT_DRAFT'
  | 'EXPIRED'
  | 'ABANDONED'
  | 'BOOKED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'AT_WAREHOUSE'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'DELIVERY_FAILED'
  | 'CANCELLED'
  | string;

/** Customer-facing words for each booking status (style.md section 9: text, never colour alone). */
export const STATUS_LABELS: Record<string, string> = {
  CHECKOUT_DRAFT: 'Awaiting payment',
  EXPIRED: 'Checkout expired',
  ABANDONED: 'Checkout discarded',
  BOOKED: 'Booked',
  ASSIGNED: 'Courier scheduled',
  PICKED_UP: 'Picked up',
  AT_WAREHOUSE: 'At warehouse',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  DELIVERY_FAILED: 'Delivery failed',
  CANCELLED: 'Cancelled',
};

export function statusLabel(status: string | null | undefined): string {
  if (!status) return 'Unknown';
  return STATUS_LABELS[status.toUpperCase()] ?? status;
}

/** Badge colour pair per style.md section 9 (the text label is always shown as well). */
export function statusBadgeClass(status: string | null | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'DELIVERED':
      return 'sd-badge--success';
    case 'OUT_FOR_DELIVERY':
      return 'sd-badge--accent';
    case 'ASSIGNED':
    case 'PICKED_UP':
    case 'AT_WAREHOUSE':
    case 'IN_TRANSIT':
      return 'sd-badge--info';
    case 'DELIVERY_FAILED':
    case 'CANCELLED':
      return 'sd-badge--danger';
    default:
      return '';
  }
}

/** Confirmed parcels that are still on their way. */
export const ACTIVE_STATUSES = [
  'BOOKED',
  'ASSIGNED',
  'PICKED_UP',
  'AT_WAREHOUSE',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERY_FAILED',
];

export type PaymentStatus =
  | 'UNPAID'
  | 'FAILED'
  | 'PAID'
  | 'PENDING'
  | 'NOT_REQUIRED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | string;

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: 'Not paid',
  FAILED: 'Payment failed',
  PAID: 'Paid online',
  PENDING: 'Pay cash on delivery',
  NOT_REQUIRED: 'No payment due',
  REFUNDED: 'Refunded',
  PARTIALLY_REFUNDED: 'Partly refunded',
};

export function paymentStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Not available';
  return PAYMENT_STATUS_LABELS[status.toUpperCase()] ?? status;
}

/** A delivery zone from GET /api/zones (managed by dispatchers). */
export interface DeliveryZone {
  id: number;
  name: string;
  code: string;
  description?: string;
  status: 'ACTIVE' | 'INACTIVE';
}

/** How the booking price was calculated by the pricing module. */
export interface BookingFeeBreakdown {
  baseFee: number | null;
  weightCharge: number | null;
  zoneCharge: number | null;
  zoneCode: string | null;
  urgentSurcharge: number | null;
  total: number | null;
  pricingRuleCode: string | null;
}

/** Response of POST /api/pricing/calculate. */
export interface PricingQuoteResponse {
  totalDeliveryFee: number;
  baseFee: number;
  weightCharge: number;
  weightKg: number;
  ratePerKg: number;
  zoneCharge: number;
  zoneCode: string;
  urgentSurcharge: number;
  urgentSurchargeApplied: boolean;
  currency: string;
  appliedRuleCode: string;
  breakdownDescription: string;
}

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
  paymentMethod?: PaymentMethod;
  paymentStatus?: PaymentStatus;
  /** True while no courier is scheduled yet (status BOOKED). */
  cancellable?: boolean;
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
  paymentStatus?: PaymentStatus;
  pricingExpiresAt?: string;
  statusUpdatedAt?: string;
  feeBreakdown?: BookingFeeBreakdown;
  pickupAddress?: AddressDto;
  deliveryAddress?: AddressDto;
}

export interface BookingReceiptDto {
  receiptNumber: string;
  parcelId: string;
  trackingNumber: string;
  bookingDate?: string;
  issuedAt: string;
  customer?: { name: string | null; email: string | null; phone: string | null };
  totalAmount: number;
  currency: string;
  feeBreakdown?: BookingFeeBreakdown;
  description?: string;
  weightKg?: number;
  urgency?: UrgencyType;
  status?: ParcelStatus;
  paymentMethod?: PaymentMethod;
  paymentStatus?: PaymentStatus;
  paidAt?: string;
  pickupAddress?: AddressDto;
  deliveryAddress?: AddressDto;
}

/** Fee estimate shown before booking. Always comes from the backend pricing module. */
export interface FeeBreakdown {
  baseFee: number;
  weightFee: number;
  ratePerKg?: number;
  zoneCharge?: number;
  zoneCode?: string;
  urgencySurcharge: number;
  urgentSurchargeApplied?: boolean;
  totalFee: number;
}

export const EMPTY_FEE: FeeBreakdown = {
  baseFee: 0,
  weightFee: 0,
  zoneCharge: 0,
  urgencySurcharge: 0,
  totalFee: 0,
};

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

/** COMPLETED: nothing to refund. REFUND_PENDING: refund requested. REFUNDED: refund done. */
export interface CancellationInitiation {
  bookingId?: string;
  trackingNumber?: string;
  cancellationStatus?: 'COMPLETED' | 'REFUND_PENDING' | 'REFUNDED' | 'NONE' | string;
  bookingStatus?: string;
  paymentMethod?: PaymentMethod;
  refundAmount?: number | null;
  cancellationFee?: number | null;
  cancelledAt?: string | null;
  completed?: boolean;
}

export interface BookingCheckout {
  id: string;
  status: string;
  checkout?: { provider: string; checkoutUrl: string; method: string; fields: Record<string, string> };
}
