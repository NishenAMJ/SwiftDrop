import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { CashHandover, CashHandoverPage, ProblemDetail } from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';

const UUIDS =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(\s*[,\n]\s*[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})*$/i;

@Component({
  selector: 'app-courier-cash-page',
  imports: [DecimalPipe, ColomboDatePipe, ReactiveFormsModule],
  templateUrl: './courier-cash-page.html',
  styleUrl: './courier-cash-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourierCashPage implements OnInit {
  private readonly api = inject(CodSettlementService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly result = signal<CashHandoverPage | null>(null);
  protected readonly selected = signal<CashHandover | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  private attempt: { fingerprint: string; key: string } | null = null;

  protected readonly form = this.fb.group({
    collectionIds: ['', [Validators.required, Validators.pattern(UUIDS)]],
    submittedAmount: [
      '',
      [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/), Validators.min(0.01)],
    ],
    currency: ['LKR', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(page = 0): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listHandovers({ page })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (result) => this.result.set(result),
        error: () => this.error.set('Your cash handover history could not be loaded. Try again.'),
      });
  }

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const collectionIds = [
      ...new Set(
        value.collectionIds
          .split(/[,\n]/)
          .map((item) => item.trim())
          .filter(Boolean),
      ),
    ];
    const request = {
      collectionIds,
      submittedAmount: Number(value.submittedAmount),
      currency: value.currency.toUpperCase(),
    };
    const fingerprint = JSON.stringify(request);
    if (this.attempt?.fingerprint !== fingerprint)
      this.attempt = { fingerprint, key: crypto.randomUUID() };
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api
      .createHandover(request, this.attempt!.key)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (handover) => {
          this.attempt = null;
          this.form.reset({ collectionIds: '', submittedAmount: '', currency: 'LKR' });
          this.notice.set(`Handover ${handover.id} was submitted for dispatcher confirmation.`);
          this.selected.set(handover);
          this.load();
        },
        error: (response: { error?: ProblemDetail; status?: number }) =>
          this.error.set(
            response.error?.detail ||
              (response.status === 409
                ? 'One or more collections are already assigned or cannot be handed over.'
                : 'The handover could not be submitted. Check the collection IDs and amount.'),
          ),
      });
  }

  protected toggle(item: CashHandover): void {
    this.selected.set(this.selected()?.id === item.id ? null : item);
  }

  protected statusClass(status: CashHandover['status']): string {
    return status === 'CONFIRMED'
      ? 'sd-badge--success'
      : status === 'FLAGGED'
        ? 'sd-badge--danger'
        : 'sd-badge--warning';
  }
}
