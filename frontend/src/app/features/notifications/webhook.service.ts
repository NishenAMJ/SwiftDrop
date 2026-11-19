import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page } from '../../core/auth/auth.models';
import {
  CreateWebhookRequest,
  UpdateWebhookRequest,
  WebhookDelivery,
  WebhookSubscription,
  WebhookSubscriptionWithSecret,
} from './webhook.models';

export const WEBHOOKS_API = '/api/v1/webhooks';

/**
 * Merchant webhooks and their delivery log (notification-webhook-service). The backend takes the
 * merchant from the access token, so a merchant only ever sees their own subscriptions.
 */
@Injectable({ providedIn: 'root' })
export class WebhookService {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 50): Observable<Page<WebhookSubscription>> {
    return this.http.get<Page<WebhookSubscription>>(`${WEBHOOKS_API}/subscriptions`, {
      params: { page, size },
    });
  }

  get(id: string): Observable<WebhookSubscription> {
    return this.http.get<WebhookSubscription>(`${WEBHOOKS_API}/subscriptions/${encodeURIComponent(id)}`);
  }

  create(request: CreateWebhookRequest): Observable<WebhookSubscriptionWithSecret> {
    return this.http.post<WebhookSubscriptionWithSecret>(`${WEBHOOKS_API}/subscriptions`, request);
  }

  update(id: string, request: UpdateWebhookRequest): Observable<WebhookSubscription> {
    return this.http.patch<WebhookSubscription>(
      `${WEBHOOKS_API}/subscriptions/${encodeURIComponent(id)}`,
      request,
    );
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${WEBHOOKS_API}/subscriptions/${encodeURIComponent(id)}`);
  }

  rotateSecret(id: string): Observable<WebhookSubscriptionWithSecret> {
    return this.http.post<WebhookSubscriptionWithSecret>(
      `${WEBHOOKS_API}/subscriptions/${encodeURIComponent(id)}/rotate-secret`,
      null,
    );
  }

  /** Queues a sample "parcel.status-changed" event to the merchant's URL. */
  sendTest(id: string): Observable<WebhookDelivery> {
    return this.http.post<WebhookDelivery>(
      `${WEBHOOKS_API}/subscriptions/${encodeURIComponent(id)}/test`,
      null,
    );
  }

  deliveries(id: string, page = 0, size = 20): Observable<Page<WebhookDelivery>> {
    return this.http.get<Page<WebhookDelivery>>(
      `${WEBHOOKS_API}/subscriptions/${encodeURIComponent(id)}/deliveries`,
      { params: { page, size } },
    );
  }

  /** Sends a failed delivery again from the first attempt. */
  retry(deliveryId: string): Observable<WebhookDelivery> {
    return this.http.post<WebhookDelivery>(
      `${WEBHOOKS_API}/deliveries/${encodeURIComponent(deliveryId)}/retry`,
      null,
    );
  }

  /** Dispatcher view: failed deliveries for every merchant, newest first. */
  failed(page = 0, size = 20): Observable<Page<WebhookDelivery>> {
    return this.http.get<Page<WebhookDelivery>>(`${WEBHOOKS_API}/deliveries`, {
      params: { status: 'FAILED', page, size },
    });
  }
}
