/** Shapes from delivery-operations-service (courier categories, assignments, proofs, issues). */

export type CategoryStatus = 'ACTIVE' | 'INACTIVE';
export type CourierProfileStatus = 'AVAILABLE' | 'OFF_DUTY' | 'INACTIVE';
export type AssignmentStatus =
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'DELIVERY_FAILED'
  | 'CANCELLED';
export type FailureReason = 'RECIPIENT_UNAVAILABLE' | 'INCORRECT_ADDRESS' | 'RECIPIENT_REFUSED' | 'OTHER';
export type IssueType =
  | 'DAMAGED_PARCEL'
  | 'ADDRESS_PROBLEM'
  | 'VEHICLE_BREAKDOWN'
  | 'ACCIDENT'
  | 'SAFETY_CONCERN'
  | 'OTHER';
export type IssueStatus = 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED' | 'DISMISSED';

export interface DeliveryPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export const EMPTY_DELIVERY_PAGE: DeliveryPage<never> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: true,
};

export interface CourierCategory {
  id: string;
  code: string;
  name: string;
  description: string | null;
  maxWeightKg: number | null;
  status: CategoryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCourierCategoryRequest {
  code: string;
  name: string;
  description: string | null;
  maxWeightKg: number | null;
}

export interface CourierProfile {
  id: string;
  identityUserId: string;
  fullName: string;
  phone: string;
  vehicleNumber: string | null;
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  status: CourierProfileStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCourierProfileRequest {
  identityUserId: string;
  fullName: string;
  phone: string;
  vehicleNumber: string | null;
  categoryId: string;
}

export interface CourierProfileFilters {
  status?: CourierProfileStatus | '';
  categoryId?: string;
  page?: number;
  size?: number;
}

/** Who to meet and where. Sent only to the assigned courier and dispatchers, and only while the job is open. */
export interface ParcelContact {
  name: string;
  phone: string;
  address: string;
}

export interface DeliveryAssignment {
  id: string;
  parcelReference: string;
  courierId: string;
  courierName: string;
  categoryCode: string;
  assignedBy: string;
  status: AssignmentStatus;
  notes: string | null;
  /** Cash to collect at the door for a merchant Cash on Delivery order; null otherwise. */
  codAmount: number | null;
  currency: string | null;
  pickup: ParcelContact | null;
  delivery: ParcelContact | null;
  assignedAt: string;
  pickedUpAt: string | null;
  outForDeliveryAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AssignParcelRequest {
  parcelReference: string;
  courierId: string;
  assignedBy: string;
  notes: string | null;
}

export interface AssignmentFilters {
  parcelReference?: string;
  courierId?: string;
  status?: AssignmentStatus | '';
  page?: number;
  size?: number;
}

/** One line of an assignment's audit trail. Dispatchers only. */
export interface StatusChange {
  id: string;
  fromStatus: AssignmentStatus | null;
  toStatus: AssignmentStatus;
  changedBy: string | null;
  changedByRole: 'DISPATCHER' | 'COURIER' | 'SYSTEM' | 'UNKNOWN';
  changedAt: string;
}

export interface RecordAttemptRequest {
  reason: FailureReason;
  notes: string | null;
  attemptedAt: string | null;
}

export interface DeliveryAttempt {
  id: string;
  assignmentId: string;
  parcelReference: string;
  attemptNumber: number;
  reason: FailureReason;
  notes: string | null;
  attemptedAt: string;
}

export interface RecordProofRequest {
  recipientName: string;
  notes: string | null;
  mediaUrl: string | null;
  deliveredAt: string | null;
  /** Required for Cash on Delivery parcels, left out for all others. */
  collectedAmount: number | null;
}

export interface ProofOfDelivery {
  id: string;
  assignmentId: string;
  parcelReference: string;
  recipientName: string;
  notes: string | null;
  mediaUrl: string | null;
  deliveredAt: string;
  collectedAmount: number | null;
}

export interface ReportIssueRequest {
  courierId: string;
  assignmentId: string | null;
  parcelReference: string | null;
  issueType: IssueType;
  description: string;
}

export interface ReviewIssueRequest {
  status: Exclude<IssueStatus, 'OPEN'>;
  reviewedBy: string;
  resolutionNotes: string | null;
}

export interface DeliveryIssue {
  id: string;
  courierId: string;
  courierName: string;
  assignmentId: string | null;
  parcelReference: string | null;
  issueType: IssueType;
  description: string;
  status: IssueStatus;
  reportedAt: string;
  reviewedBy: string | null;
  resolutionNotes: string | null;
  reviewedAt: string | null;
}

export interface IssueFilters {
  status?: IssueStatus | '';
  courierId?: string;
  page?: number;
  size?: number;
}

// Labels and badge colours. Status is always shown as text, never by colour alone (style.md section 9).

export const ASSIGNMENT_STATUS_LABELS: Record<AssignmentStatus, string> = {
  ASSIGNED: 'Assigned',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  DELIVERY_FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export function assignmentStatusClass(status: AssignmentStatus): string {
  switch (status) {
    case 'DELIVERED':
      return 'sd-badge--success';
    case 'DELIVERY_FAILED':
    case 'CANCELLED':
      return 'sd-badge--danger';
    case 'OUT_FOR_DELIVERY':
      return 'sd-badge--accent';
    case 'PICKED_UP':
    case 'IN_TRANSIT':
      return 'sd-badge--info';
    default:
      return '';
  }
}

export const COURIER_STATUS_LABELS: Record<CourierProfileStatus, string> = {
  AVAILABLE: 'Available',
  OFF_DUTY: 'Off duty',
  INACTIVE: 'Inactive',
};

export const FAILURE_REASON_LABELS: Record<FailureReason, string> = {
  RECIPIENT_UNAVAILABLE: 'Recipient unavailable',
  INCORRECT_ADDRESS: 'Incorrect address',
  RECIPIENT_REFUSED: 'Recipient refused the parcel',
  OTHER: 'Other reason',
};

export const ISSUE_TYPE_LABELS: Record<IssueType, string> = {
  DAMAGED_PARCEL: 'Damaged parcel',
  ADDRESS_PROBLEM: 'Address problem',
  VEHICLE_BREAKDOWN: 'Vehicle breakdown',
  ACCIDENT: 'Accident',
  SAFETY_CONCERN: 'Safety concern',
  OTHER: 'Other',
};

export const ISSUE_STATUS_LABELS: Record<IssueStatus, string> = {
  OPEN: 'Open',
  UNDER_REVIEW: 'Under review',
  RESOLVED: 'Resolved',
  DISMISSED: 'Dismissed',
};

export function issueStatusClass(status: IssueStatus): string {
  if (status === 'RESOLVED') return 'sd-badge--success';
  if (status === 'UNDER_REVIEW') return 'sd-badge--info';
  if (status === 'OPEN') return 'sd-badge--warning';
  return '';
}

/** The one step a courier can take next; delivered and failed have their own forms. */
export function nextStep(
  status: AssignmentStatus,
): { to: AssignmentStatus; label: string } | null {
  switch (status) {
    case 'ASSIGNED':
      return { to: 'PICKED_UP', label: 'Mark as picked up' };
    case 'PICKED_UP':
      return { to: 'IN_TRANSIT', label: 'Mark as in transit' };
    case 'IN_TRANSIT':
      return { to: 'OUT_FOR_DELIVERY', label: 'Start delivery' };
    default:
      return null;
  }
}
