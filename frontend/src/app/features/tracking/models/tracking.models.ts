export interface TrackingSummary {
  trackingNumber: string;
  currentStatus: string;
  publicDescription: string;
  safeLocation: string | null;
  latestUpdateTime: string;
}

export interface TrackingHistoryItem {
  status: string;
  publicDescription: string;
  safeLocation: string | null;
  eventTime: string;
}

export interface TrackingHistory {
  trackingNumber: string;
  events: TrackingHistoryItem[];
}

export interface TrackingResult {
  summary: TrackingSummary;
  history: TrackingHistory;
}

export interface ReportSummary {
  startDate: string | null;
  endDate: string | null;
  totalParcelsBooked: number;
  parcelsCurrentlyInTransit: number;
  deliveredParcels: number;
  failedDeliveries: number;
  deliverySuccessRate: number;
}

export interface StatusTotal {
  status: string;
  total: number;
}

export interface StatusBreakdown {
  startDate: string | null;
  endDate: string | null;
  statuses: StatusTotal[];
}

export interface DeliveryCount {
  period: string;
  deliveredParcels: number;
}

export interface DeliveryCounts {
  startDate: string | null;
  endDate: string | null;
  counts: DeliveryCount[];
}

export interface ReportFilters {
  startDate?: string;
  endDate?: string;
}

export type DeliveryInterval = 'daily' | 'monthly';

export interface ReportDashboardData {
  summary: ReportSummary;
  statusBreakdown: StatusBreakdown;
  deliveryCounts: DeliveryCounts;
}

/** Privacy-safe state sent by the authenticated tracking SSE endpoint. */
export interface TrackingStreamState {
  trackingNumber: string;
  status: string;
  safeLocationLabel: string | null;
  updatedAt: string;
}

export type TrackingConnectionEvent =
  | { type: 'connected' }
  | { type: 'reconnecting'; attempt: number }
  | { type: 'update'; state: TrackingStreamState };
