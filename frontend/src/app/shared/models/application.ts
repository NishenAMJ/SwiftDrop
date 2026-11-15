/**
 * Merchant and courier applications share one status flow
 * (contracts/openapi/identity-merchant-service.yaml):
 * PENDING -> APPROVED / REJECTED, APPROVED <-> SUSPENDED, REJECTED -> PENDING on resubmit.
 */
export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

/** A dispatcher's last decision. `decidedBy` is only sent to dispatchers. */
export interface StatusDecision {
  status: ApplicationStatus;
  reason?: string | null;
  decidedAt: string;
  decidedBy?: string | null;
}

/** The decisions a dispatcher can take, and the only status each one is allowed from. */
export type ApplicationDecision = 'approve' | 'reject' | 'suspend' | 'reactivate';

export const DECISIONS_BY_STATUS: Record<ApplicationStatus, readonly ApplicationDecision[]> = {
  PENDING: ['approve', 'reject'],
  APPROVED: ['suspend'],
  SUSPENDED: ['reactivate'],
  // A rejected applicant fixes and resubmits; there is nothing for a dispatcher to do.
  REJECTED: [],
};

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  PENDING: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

/** The status a dispatcher's last decision led to, as a past action ("Rejected on 3 Nov"). */
export const DECISION_LABELS: Record<ApplicationStatus, string> = {
  PENDING: 'Sent for review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

/** E.164 phone numbers, the same rule as the server and the register page. */
export const PHONE_E164 = /^\+[1-9][0-9]{7,14}$/;
