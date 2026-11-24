import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { MerchantLedgerPage, MerchantSettlement, MerchantSettlementPage, ProblemDetail } from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
type Operation = 'ledger' | 'adjustment' | 'settlement';

@Component({
  selector: 'app-settlement-operations-page',
  imports: [DecimalPipe, ColomboDatePipe, ReactiveFormsModule],
  templateUrl: './settlement-operations-page.html',
  styleUrl: './settlement-operations-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettlementOperationsPage implements OnInit {
  private readonly api = inject(CodSettlementService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly operation = signal<Operation>('ledger');
  protected readonly ledger = signal<MerchantLedgerPage | null>(null);
  protected readonly settlement = signal<MerchantSettlement | null>(null);
  protected readonly settlements = signal<MerchantSettlementPage | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal<string | null>(null);
  protected readonly historyMerchantId = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  private attempts = new Map<string, { fingerprint: string; key: string }>();

  protected readonly ledgerForm = this.fb.group({
    merchantId: ['', [Validators.required, Validators.pattern(UUID)]],
    currency: ['LKR', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
  });
  protected readonly adjustmentForm = this.fb.group({
    merchantId: ['', [Validators.required, Validators.pattern(UUID)]],
    amount: ['', [Validators.required, Validators.pattern(/^-?\d+(\.\d{1,2})?$/)]],
    currency: ['LKR', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    sourceReference: ['', [Validators.required, Validators.maxLength(200)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });
  protected readonly calculateForm = this.fb.group({
    merchantId: ['', [Validators.required, Validators.pattern(UUID)]],
    currency: ['LKR', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    cutoffAt: ['', Validators.required],
  });
  protected readonly lookupForm = this.fb.group({
    settlementId: ['', [Validators.required, Validators.pattern(UUID)]],
  });
  protected readonly settlementHistoryForm = this.fb.group({
    merchantId: ['', [Validators.required, Validators.pattern(UUID)]],
  });
  protected readonly approvalForm = this.fb.group({
    note: ['', Validators.maxLength(500)],
    confirm: [false, Validators.requiredTrue],
  });
  protected readonly paymentForm = this.fb.group({
    externalReference: ['', [Validators.required, Validators.maxLength(200)]],
    paidAt: ['', Validators.required],
    confirm: [false, Validators.requiredTrue],
  });

  ngOnInit(): void {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    this.calculateForm.controls.cutoffAt.setValue(now.toISOString().slice(0, 16));
    this.paymentForm.controls.paidAt.setValue(now.toISOString().slice(0, 16));
  }

  protected show(operation: Operation): void {
    this.operation.set(operation);
    this.clearMessages();
  }

  protected loadLedger(page = 0, preserveNotice = false): void {
    if (this.ledgerForm.invalid) {
      this.ledgerForm.markAllAsTouched();
      return;
    }
    const value = this.ledgerForm.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    if (!preserveNotice) this.notice.set(null);
    this.api
      .listLedger({
        merchantId: value.merchantId.trim(),
        currency: value.currency.toUpperCase(),
        page,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (ledger) => this.ledger.set(ledger),
        error: (response) => this.fail(response, 'Merchant ledger could not be loaded.'),
      });
  }

  protected createAdjustment(): void {
    if (this.adjustmentForm.invalid || this.busy()) {
      this.adjustmentForm.markAllAsTouched();
      return;
    }
    const value = this.adjustmentForm.getRawValue();
    const amount = Number(value.amount);
    if (amount === 0) {
      this.adjustmentForm.controls.amount.setErrors({ nonZero: true });
      return;
    }
    const request = {
      merchantId: value.merchantId.trim(),
      amount,
      currency: value.currency.toUpperCase(),
      sourceReference: value.sourceReference.trim(),
      reason: value.reason.trim(),
    };
    this.run(
      'adjustment',
      request,
      (key) => this.api.createAdjustment(request, key),
      'Ledger adjustment posted. The original entries were not changed.',
      () => {
        this.adjustmentForm.reset({
          merchantId: request.merchantId,
          amount: '',
          currency: request.currency,
          sourceReference: '',
          reason: '',
        });
        this.ledgerForm.setValue({ merchantId: request.merchantId, currency: request.currency });
        this.loadLedger(0, true);
      },
    );
  }

  protected calculate(): void {
    if (this.calculateForm.invalid || this.busy()) {
      this.calculateForm.markAllAsTouched();
      return;
    }
    const value = this.calculateForm.getRawValue();
    const request = {
      merchantId: value.merchantId.trim(),
      currency: value.currency.toUpperCase(),
      cutoffAt: new Date(value.cutoffAt).toISOString(),
    };
    this.run(
      'calculate',
      request,
      (key) => this.api.calculateSettlement(request, key),
      'Settlement calculated and its ledger entries are now frozen.',
      (result) => {
        this.settlement.set(result as MerchantSettlement);
        this.lookupForm.controls.settlementId.setValue((result as MerchantSettlement).id);
        this.settlementHistoryForm.controls.merchantId.setValue(request.merchantId);
        this.loadSettlementHistory();
      },
    );
  }

  protected findSettlement(): void {
    if (this.lookupForm.invalid || this.busy()) {
      this.lookupForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.clearMessages();
    this.api
      .getSettlement(this.lookupForm.controls.settlementId.value.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (item) => this.settlement.set(item),
        error: (response) => this.fail(response, 'Settlement could not be found.'),
      });
  }

  protected loadSettlementHistory(page = 0): void {
    if (this.historyLoading()) return;
    if (this.settlementHistoryForm.invalid) {
      this.settlementHistoryForm.markAllAsTouched();
      return;
    }
    const merchantId = this.settlementHistoryForm.controls.merchantId.value.trim();
    if (merchantId !== this.historyMerchantId()) page = 0;
    this.historyMerchantId.set(merchantId);
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.settlements.set(null);
    this.api
      .listSettlements({ merchantId, page })
      .pipe(finalize(() => this.historyLoading.set(false)))
      .subscribe({
        next: (settlements) => this.settlements.set(settlements),
        error: () => this.historyError.set('Settlement history could not be loaded. Try again.'),
      });
  }

  protected selectSettlement(item: MerchantSettlement): void {
    if (this.busy()) return;
    this.settlement.set(item);
    this.lookupForm.controls.settlementId.setValue(item.id);
    this.approvalForm.reset({ note: '', confirm: false });
    this.paymentForm.patchValue({ externalReference: '', confirm: false });
  }

  private updateHistory(item: MerchantSettlement): void {
    this.settlements.update((page) => page ? {
      ...page, content: page.content.map((entry) => entry.id === item.id ? item : entry),
    } : null);
  }

  protected approve(): void {
    const item = this.settlement();
    if (!item || this.approvalForm.invalid || this.busy()) {
      this.approvalForm.markAllAsTouched();
      return;
    }
    const note = this.approvalForm.controls.note.value.trim();
    this.run(
      `approve-${item.id}`,
      { id: item.id, note },
      (key) => this.api.approveSettlement(item.id, note, key),
      'Settlement approved. No payment has been recorded yet.',
      (result) => {
        this.settlement.set(result as MerchantSettlement);
        this.approvalForm.reset({ note: '', confirm: false });
        this.updateHistory(result as MerchantSettlement);
      },
    );
  }

  protected recordPayment(): void {
    const item = this.settlement();
    if (!item || this.paymentForm.invalid || this.busy()) {
      this.paymentForm.markAllAsTouched();
      return;
    }
    const value = this.paymentForm.getRawValue();
    const request = {
      externalReference: value.externalReference.trim(),
      paidAt: new Date(value.paidAt).toISOString(),
    };
    this.run(
      `payment-${item.id}`,
      request,
      (key) => this.api.recordSettlementPayment(item.id, request, key),
      'External payout recorded and the settlement ledger debit was created.',
      (result) => {
        this.settlement.set(result as MerchantSettlement);
        this.paymentForm.patchValue({ externalReference: '', confirm: false });
        this.updateHistory(result as MerchantSettlement);
      },
    );
  }

  protected settlementClass(status: MerchantSettlement['status']): string {
    return status === 'PAID'
      ? 'sd-badge--success'
      : status === 'APPROVED'
        ? 'sd-badge--info'
        : 'sd-badge--warning';
  }

  private run<T>(
    name: string,
    payload: unknown,
    request: (key: string) => import('rxjs').Observable<T>,
    success: string,
    after?: (result: T) => void,
  ): void {
    const fingerprint = JSON.stringify(payload);
    const current = this.attempts.get(name);
    if (current?.fingerprint !== fingerprint)
      this.attempts.set(name, { fingerprint, key: crypto.randomUUID() });
    this.busy.set(true);
    this.clearMessages();
    request(this.attempts.get(name)!.key)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (result) => {
          this.attempts.delete(name);
          this.notice.set(success);
          after?.(result);
        },
        error: (response) =>
          this.fail(
            response,
            'The operation could not be completed. Review the current state and try again.',
          ),
      });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
  private fail(response: { error?: ProblemDetail; status?: number }, fallback: string): void {
    this.error.set(
      response.error?.detail ||
        (response.status === 409
          ? 'The record changed or conflicts with another operation. Refresh it before continuing.'
          : fallback),
    );
  }
}
