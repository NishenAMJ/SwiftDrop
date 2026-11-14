import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';

import { errorMessage } from '../../../core/auth/problem-messages';
import { ApplicationStatusBadge } from '../../../shared/components/application-status-badge/application-status-badge';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Pagination } from '../../../shared/components/pagination/pagination';
import {
  ApplicationDecision,
  ApplicationStatus,
  DECISIONS_BY_STATUS,
  DECISION_LABELS,
  STATUS_LABELS,
} from '../../../shared/models/application';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { Page } from '../../../core/auth/auth.models';
import { BUSINESS_TYPE_LABELS, Merchant, formatAddress } from '../../merchant/merchant.models';
import { DECISION_COPY, DecisionFlow } from '../decision-flow';
import { DispatcherService } from '../dispatcher.service';

const PAGE_SIZE = 20;

/**
 * The dispatcher's merchant applications: pending ones first (the approval queue), and every
 * other status on request. Decisions are confirmed in a dialog; reasons are shown to the merchant.
 */
@Component({
  selector: 'app-merchant-review-page',
  imports: [ReactiveFormsModule, ApplicationStatusBadge, ConfirmDialog, Pagination, ColomboDatePipe],
  templateUrl: './merchant-review-page.html',
  styleUrl: './merchant-review-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantReviewPage implements OnInit {
  private readonly dispatcher = inject(DispatcherService);
  private readonly injector = inject(Injector);

  protected readonly statuses = Object.entries(STATUS_LABELS) as [ApplicationStatus, string][];
  protected readonly businessTypeLabels = BUSINESS_TYPE_LABELS;
  protected readonly formatAddress = formatAddress;
  protected readonly copy = DECISION_COPY;
  protected readonly decisionLabels = DECISION_LABELS;
  protected readonly decisionsFor = (status: ApplicationStatus) => DECISIONS_BY_STATUS[status];

  protected readonly filters = inject(NonNullableFormBuilder).group({
    status: ['PENDING' as ApplicationStatus | ''],
    q: [''],
  });

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly result = signal<Page<Merchant> | null>(null);
  protected readonly page = signal(0);
  protected readonly notice = signal<{ text: string; tone: 'success' | 'info' } | null>(null);

  private readonly resultsHeading = viewChild<ElementRef<HTMLElement>>('resultsHeading');

  protected readonly decision = new DecisionFlow<Merchant>(
    (merchant, decision, body) => this.dispatcher.decideMerchant(merchant.id, decision, body),
    ({ message, stale }) => {
      this.notice.set({ text: message, tone: stale ? 'info' : 'success' });
      this.load({ focusResults: true });
    },
  );

  ngOnInit(): void {
    this.load();
  }

  protected search(): void {
    this.notice.set(null);
    this.page.set(0);
    this.load({ focusResults: true });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.load({ focusResults: true });
  }

  protected ask(merchant: Merchant, decision: ApplicationDecision): void {
    this.decision.ask(merchant, merchant.businessName, decision);
  }

  protected load(options: { focusResults?: boolean } = {}): void {
    const { status, q } = this.filters.getRawValue();
    this.loading.set(true);
    this.loadError.set(null);
    this.dispatcher
      .listMerchants({ status: status || undefined, q: q.trim() || undefined, page: this.page(), size: PAGE_SIZE })
      .subscribe({
        next: (result) => {
          // A decision can empty the last page; step back so the list isn't blank.
          if (result.content.length === 0 && this.page() > 0 && result.totalPages > 0) {
            this.page.set(result.totalPages - 1);
            this.load(options);
            return;
          }
          this.result.set(result);
          this.loading.set(false);
          if (options.focusResults) {
            afterNextRender(() => this.resultsHeading()?.nativeElement.focus(), { injector: this.injector });
          }
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadError.set(errorMessage(error));
        },
      });
  }
}
