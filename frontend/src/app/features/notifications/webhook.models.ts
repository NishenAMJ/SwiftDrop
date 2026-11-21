/** Shapes from contracts/openapi/notification-webhook-service.yaml (notification-webhook-service). */

export interface WebhookSubscription {
  id: string;
  merchantId: string;
  url: string;
  eventTypes: string[];
  /** False when the merchant paused it, or SwiftDrop paused it (see `pausedReason`). */
  active: boolean;
  pausedReason?: string;
  createdAt: string;
  updatedAt: string;
}

/** Returned only when a subscription is created or its secret is rotated. Shown once. */
export interface WebhookSubscriptionWithSecret extends WebhookSubscription {
  secret: string;
}

export interface CreateWebhookRequest {
  url: string;
  eventTypes: string[];
}

export interface UpdateWebhookRequest {
  url?: string;
  eventTypes?: string[];
  active?: boolean;
}

export type WebhookDeliveryStatus = 'PENDING' | 'DELIVERED' | 'FAILED';

export interface WebhookDelivery {
  id: string;
  subscriptionId: string;
  eventId: string;
  eventType: string;
  status: WebhookDeliveryStatus;
  attemptCount: number;
  nextAttemptAt?: string;
  lastStatusCode?: number;
  lastError?: string;
  createdAt: string;
  deliveredAt?: string;
}

/** Events a merchant can subscribe to. Add new ones here when the backend supports them. */
export const WEBHOOK_EVENTS: readonly { value: string; label: string; help: string }[] = [
  {
    value: 'parcel.status-changed',
    label: 'Parcel status changes',
    help: 'When one of your parcels is picked up, in transit, out for delivery, delivered or failed.',
  },
];

export function eventLabel(value: string): string {
  return WEBHOOK_EVENTS.find((event) => event.value === value)?.label ?? value;
}

/** Status in words; colour only supports the text (frontend/style.md section 9). */
export const DELIVERY_STATUS: Record<WebhookDeliveryStatus, { label: string; badge: string }> = {
  PENDING: { label: 'Sending', badge: 'sd-badge sd-badge--info' },
  DELIVERED: { label: 'Delivered', badge: 'sd-badge sd-badge--success' },
  FAILED: { label: 'Failed', badge: 'sd-badge sd-badge--danger' },
};

export function subscriptionBadge(subscription: WebhookSubscription): { label: string; badge: string } {
  return subscription.active
    ? { label: 'Active', badge: 'sd-badge sd-badge--success' }
    : { label: 'Paused', badge: 'sd-badge sd-badge--warning' };
}

/** A short, human reason for the last failed attempt. Never raw server text. */
export function attemptResult(delivery: WebhookDelivery): string {
  if (delivery.status === 'DELIVERED') {
    return delivery.lastStatusCode ? `Accepted (HTTP ${delivery.lastStatusCode})` : 'Accepted';
  }
  if (delivery.lastStatusCode) {
    return `Your server answered HTTP ${delivery.lastStatusCode}`;
  }
  if (delivery.lastError) {
    return 'Could not reach your server';
  }
  return delivery.attemptCount === 0 ? 'Not tried yet' : '';
}
