import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';

import { Page } from '../../../core/auth/auth.models';
import { errorMessage } from '../../../core/auth/problem-messages';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { WebhookDelivery, attemptResult, eventLabel } from '../webhook.models';
import { WebhookService } from '../webhook.service';

const PAGE_SIZE = 20;

/**
 * Dispatcher monitoring: webhook deliveries that failed after every retry, for all merchants.
 * Read only; merchants send their own deliveries again. No payloads or merchant URLs are shown.
 */
@Component({
  selector: 'app-failed-webhooks-page',
  imports: [ColomboDatePipe],
  templateUrl: './failed-webhooks-page.html',
  styleUrl: './failed-webhooks-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FailedWebhooksPage implements OnInit {
  private readonly webhooks = inject(WebhookService);

  protected readonly eventLabel = eventLabel;
  protected readonly outcome = attemptResult;

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly page = signal<Page<WebhookDelivery> | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  protected load(pageNumber = this.page()?.page ?? 0): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.webhooks.failed(pageNumber, PAGE_SIZE).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.loadError.set(errorMessage(error));
      },
    });
  }
}
