import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/auth/problem-messages';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  DeliveryIssue,
  DeliveryPage,
  EMPTY_DELIVERY_PAGE,
  ISSUE_STATUS_LABELS,
  ISSUE_TYPE_LABELS,
  IssueStatus,
  ReviewIssueRequest,
  issueStatusClass,
} from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';

type ReviewDecision = ReviewIssueRequest['status'];

const FILTER_STATUSES: IssueStatus[] = ['OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED'];

@Component({
  selector: 'app-delivery-issues-page',
  imports: [ColomboDatePipe, ReactiveFormsModule],
  templateUrl: './delivery-issues-page.html',
  styleUrl: './delivery-issues-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeliveryIssuesPage implements OnInit {
  private readonly service = inject(CourierDeliveryService);
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly filterStatuses = FILTER_STATUSES;
  protected readonly statusLabels = ISSUE_STATUS_LABELS;
  protected readonly typeLabels = ISSUE_TYPE_LABELS;

  protected readonly issues = signal<DeliveryPage<DeliveryIssue>>(EMPTY_DELIVERY_PAGE);
  protected readonly loading = signal(true);
  protected readonly reviewingId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly filters = this.formBuilder.group({ status: ['OPEN' as IssueStatus | ''] });

  protected readonly reviewForm = this.formBuilder.group({
    status: ['' as ReviewDecision | '', Validators.required],
    resolutionNotes: ['', Validators.maxLength(1000)],
  });

  ngOnInit(): void {
    this.load(0);
  }

  protected applyFilters(): void {
    this.load(0);
  }

  protected previousPage(): void {
    const page = this.issues();
    if (!page.first) this.load(page.number - 1);
  }

  protected nextPage(): void {
    const page = this.issues();
    if (!page.last) this.load(page.number + 1);
  }

  protected beginReview(issue: DeliveryIssue): void {
    if (issue.status === 'RESOLVED' || issue.status === 'DISMISSED') return;
    this.reviewingId.set(issue.id);
    this.submitted.set(false);
    this.reviewForm.reset({ status: '', resolutionNotes: issue.resolutionNotes ?? '' });
    this.clearMessages();
  }

  protected closeReview(): void {
    this.reviewingId.set(null);
    this.submitted.set(false);
  }

  /** An issue that is already under review can only be closed. */
  protected decisionsFor(issue: DeliveryIssue): ReviewDecision[] {
    return issue.status === 'OPEN'
      ? ['UNDER_REVIEW', 'RESOLVED', 'DISMISSED']
      : ['RESOLVED', 'DISMISSED'];
  }

  protected saveReview(issue: DeliveryIssue): void {
    this.submitted.set(true);
    this.clearMessages();
    if (this.reviewingId() !== issue.id || this.reviewForm.invalid || this.saving()) {
      this.reviewForm.markAllAsTouched();
      return;
    }
    const value = this.reviewForm.getRawValue();
    this.saving.set(true);
    this.service
      .reviewIssue(issue.id, {
        status: value.status as ReviewDecision,
        // The signed-in dispatcher is recorded as the reviewer.
        reviewedBy: this.auth.user()?.fullName ?? 'Dispatcher',
        resolutionNotes: value.resolutionNotes.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.closeReview();
          this.notice.set('Issue updated.');
          this.load(this.issues().number, false);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected statusClass(status: IssueStatus): string {
    return issueStatusClass(status);
  }

  protected reviewError(field: 'status' | 'resolutionNotes'): string | null {
    const control = this.reviewForm.controls[field];
    if (!(control.touched || this.submitted()) || !control.errors) return null;
    if (control.errors['required']) return 'Choose an outcome.';
    return 'Use no more than 1000 characters.';
  }

  private load(page: number, showLoading = true): void {
    if (showLoading) this.loading.set(true);
    this.error.set(null);
    this.service
      .listIssues({ status: this.filters.getRawValue().status, page, size: 20 })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => this.issues.set(response),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
