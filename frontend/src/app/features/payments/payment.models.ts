export type PaymentStatus =
  | 'PENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED'
  | 'CHARGEBACKED';

export interface Payment {
  id: string;
  bookingId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  refundedAmount: number;
  provider?: string;
  providerReference?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentPage {
  content: Payment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Refund {
  id: string;
  paymentId: string;
  amount: number;
  currency: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

export interface RecoveryAudit {
  id: string;
  paymentId: string;
  refundId?: string | null;
  action: 'REFUND_CONFIRMED_SUCCESS' | 'REFUND_CONFIRMED_FAILURE' | 'BOOKING_CLAIM_RELEASED';
  priorStatus: string;
  resultingStatus: string;
  evidenceType: 'PAYHERE_DASHBOARD' | 'PAYHERE_SUPPORT_CASE';
  evidenceReference: string;
  providerReference?: string | null;
  reason: string;
  createdAt: string;
}

export type EvidenceType = 'PAYHERE_DASHBOARD' | 'PAYHERE_SUPPORT_CASE';


export interface CancellationRefund {
  eventId: string;
  bookingId: string;
  paymentId: string | null;
  refundId: string | null;
  status: 'PENDING' | 'COMPLETED' | 'ATTENTION_REQUIRED';
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface CancellationRefundPage {
  content: CancellationRefund[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
