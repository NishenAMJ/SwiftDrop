import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Page } from '../../../core/auth/auth.models';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  DELIVERY_STATUS,
  WebhookDelivery,
  WebhookSubscription,
  attemptResult,
  eventLabel,
  subscriptionBadge,
} from '../webhook.models';
import { WebhookService } from '../webhook.service';

const PAGE_SIZE = 20;

const ERRORS: Record<string, string> = {
  SUBSCRIPTION_NOT_FOUND: "This webhook doesn't exist, or it isn't yours.",
  DELIVERY_NOT_FOUND: 'That delivery no longer exists. The list has been refreshed.',
  DELIVERY_NOT_RETRYABLE: 'Only failed deliveries can be sent again. The list has been refreshed.',
  MERCHANT_NOT_APPROVED: 'Webhooks are available once your merchant account is approved.',
};

/**
 * The delivery log of one webhook: each event SwiftDrop tried to send, how many attempts it took
 * and what the merchant's server answered. Failed deliveries can be sent again.
 */
@Component({
  selector: 'app-webhook-deliveries-page',
  imports: [RouterLink, ColomboDatePipe],
  templateUrl: './webhook-deliveries-page.html',
  styleUrl: './webhook-deliveries-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WebhookDeliveriesPage implements OnInit {
  private readonly webhooks = inject(WebhookService);

  /** The subscription id from the route, ':id'. */
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  protected readonly status = DELIVERY_STATUS;
  protected readonly eventLabel = eventLabel;
  protected readonly outcome = attemptResult;
  protected readonly badge = subscriptionBadge;

  protected readonly subscription = signal<WebhookSubscription | null>(null);
  protected readonly notFound = signal(false);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly page = signal<Page<WebhookDelivery> | null>(null);

  protected readonly retryingId = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly retryError = signal<string | null>(null);

  ngOnInit(): void {
    this.webhooks.get(this.id).subscribe({
      next: (subscription) => this.subscription.set(subscription),
      error: (error: unknown) => {
        if (readProblem(error).code === 'SUBSCRIPTION_NOT_FOUND') {
          this.notFound.set(true);
          this.loading.set(false);
        }
      },
    });
    this.load(0);
  }

  protected load(pageNumber = this.page()?.page ?? 0): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.webhooks.deliveries(this.id, pageNumber, PAGE_SIZE).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (readProblem(error).code === 'SUBSCRIPTION_NOT_FOUND') {
          this.notFound.set(true);
          return;
        }
        this.loadError.set(errorMessage(error, ERRORS));
      },
    });
  }

  protected retry(delivery: WebhookDelivery): void {
    if (this.retryingId()) {
      return;
    }
    this.retryingId.set(delivery.id);
    this.notice.set(null);
    this.retryError.set(null);
    this.webhooks.retry(delivery.id).subscribe({
      next: () => {
        this.retryingId.set(null);
        this.notice.set('Sending again. Refresh in a few seconds to see the result.');
        this.load();
      },
      error: (error: unknown) => {
        this.retryingId.set(null);
        const code = readProblem(error).code;
        if (code === 'DELIVERY_NOT_RETRYABLE' || code === 'DELIVERY_NOT_FOUND') {
          this.notice.set(ERRORS[code]);
          this.load();
          return;
        }
        this.retryError.set(errorMessage(error, ERRORS));
      },
    });
  }
}
