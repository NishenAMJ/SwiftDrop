import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  CashHandover,
  CashHandoverPage,
  HandoverStatus,
  ProblemDetail,
} from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';

@Component({
  selector: 'app-cash-reconciliation-page',
  imports: [DecimalPipe, ColomboDatePipe, ReactiveFormsModule],
  templateUrl: './cash-reconciliation-page.html',
  styleUrl: './cash-reconciliation-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CashReconciliationPage implements OnInit {
  private readonly api = inject(CodSettlementService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly result = signal<CashHandoverPage | null>(null);
  protected readonly selected = signal<CashHandover | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  private attempt: { fingerprint: string; key: string } | null = null;

  protected readonly filters = this.fb.group({
    courierId: [''],
    status: ['SUBMITTED' as HandoverStatus | ''],
  });
  protected readonly form = this.fb.group({
    receivedAmount: [
      '',
      [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/), Validators.min(0)],
    ],
    investigationNote: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(page = 0): void {
    const filters = this.filters.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listHandovers({ page, courierId: filters.courierId, status: filters.status })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (result) => this.result.set(result),
        error: () =>
          this.error.set('Cash handovers could not be loaded. Check the filters and try again.'),
      });
  }

  protected select(item: CashHandover): void {
    this.selected.set(this.selected()?.id === item.id ? null : item);
    this.form.reset({ receivedAmount: item.submittedAmount.toFixed(2), investigationNote: '' });
    this.error.set(null);
    this.notice.set(null);
    this.attempt = null;
  }

  protected confirm(item: CashHandover): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const receivedAmount = Number(value.receivedAmount);
    const investigationNote = value.investigationNote.trim();
    if (receivedAmount !== item.expectedAmount && !investigationNote) {
      this.form.controls.investigationNote.setErrors({ requiredForDifference: true });
      this.form.controls.investigationNote.markAsTouched();
      return;
    }
    const request = { receivedAmount, ...(investigationNote ? { investigationNote } : {}) };
    const fingerprint = `${item.id}|${JSON.stringify(request)}`;
    if (this.attempt?.fingerprint !== fingerprint)
      this.attempt = { fingerprint, key: crypto.randomUUID() };
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api
      .confirmHandover(item.id, request, this.attempt!.key)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => {
          this.attempt = null;
          this.selected.set(updated);
          this.notice.set(
            updated.status === 'CONFIRMED'
              ? 'Cash handover confirmed with an exact balance.'
              : 'Cash handover reconciled and flagged for investigation.',
          );
          this.load(this.result()?.page ?? 0);
        },
        error: (response: { error?: ProblemDetail; status?: number }) =>
          this.error.set(
            response.error?.detail ||
              (response.status === 409
                ? 'This handover has already been finalized. Refresh the list.'
                : 'The reconciliation could not be recorded.'),
          ),
      });
  }

  protected statusClass(status: CashHandover['status']): string {
    return status === 'CONFIRMED'
      ? 'sd-badge--success'
      : status === 'FLAGGED'
        ? 'sd-badge--danger'
        : 'sd-badge--warning';
  }
}
