export type HandoverStatus = 'SUBMITTED' | 'CONFIRMED' | 'FLAGGED';
export type ReconciliationStatus = 'EXACT' | 'SHORT' | 'EXCESS';

export interface CashReconciliation {
  id: string;
  expectedAmount: number;
  receivedAmount: number;
  differenceAmount: number;
  currency: string;
  status: ReconciliationStatus;
  investigationNote?: string | null;
  confirmedBy: string;
  confirmedAt: string;
}

export interface CashHandover {
  id: string;
  courierId: string;
  collectionIds: string[];
  expectedAmount: number;
  submittedAmount: number;
  currency: string;
  status: HandoverStatus;
  submittedAt: string;
  reconciliation?: CashReconciliation | null;
}

export interface CashHandoverPage {
  content: CashHandover[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type LedgerEntryType = 'COD_COLLECTION' | 'DELIVERY_CHARGE' | 'ADJUSTMENT' | 'SETTLEMENT';

export interface MerchantLedgerEntry {
  id: string;
  merchantId: string;
  amount: number;
  currency: string;
  entryType: LedgerEntryType;
  sourceReference: string;
  idempotencyKey: string;
  reason?: string | null;
  settlementId?: string | null;
  settlementEligible?: boolean | null;
  createdAt: string;
}

export interface MerchantLedgerPage {
  content: MerchantLedgerEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  balance: number;
  currency: string;
}

export type SettlementStatus = 'CALCULATED' | 'APPROVED' | 'PAID';

export interface MerchantSettlement {
  id: string;
  merchantId: string;
  ledgerEntryIds: string[];
  amount: number;
  currency: string;
  cutoffAt: string;
  status: SettlementStatus;
  calculatedBy: string;
  calculatedAt: string;
  approvedBy?: string | null;
  approvedAt?: string | null;
  approvalNote?: string | null;
  paidBy?: string | null;
  paidAt?: string | null;
  externalPaymentReference?: string | null;
}

export interface MerchantSettlementPage {
  content: MerchantSettlement[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}
