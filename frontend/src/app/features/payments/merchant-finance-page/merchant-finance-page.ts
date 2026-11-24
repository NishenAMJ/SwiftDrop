import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { MerchantLedgerPage, MerchantSettlement, MerchantSettlementPage, ProblemDetail } from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

@Component({
  selector: 'app-merchant-finance-page',
  imports: [DecimalPipe, ColomboDatePipe, ReactiveFormsModule],
  templateUrl: './merchant-finance-page.html',
  styleUrl: './merchant-finance-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantFinancePage implements OnInit {
  private readonly api = inject(CodSettlementService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly ledger = signal<MerchantLedgerPage | null>(null);
  protected readonly settlement = signal<MerchantSettlement | null>(null);
  protected readonly settlements = signal<MerchantSettlementPage | null>(null);
  protected readonly loading = signal(true);
  protected readonly settlementsLoading = signal(true);
  protected readonly lookupBusy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly settlementError = signal<string | null>(null);
  protected readonly historyError = signal<string | null>(null);
  protected readonly currency = signal('LKR');

  protected readonly currencyForm = this.fb.group({
    currency: ['LKR', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
  });
  protected readonly lookupForm = this.fb.group({
    settlementId: ['', [Validators.required, Validators.pattern(UUID)]],
  });

  ngOnInit(): void {
    this.loadLedger();
    this.loadSettlements();
  }

  protected loadSettlements(page = 0): void {
    this.settlementsLoading.set(true);
    this.historyError.set(null);
    this.api
      .listSettlements({ page })
      .pipe(finalize(() => this.settlementsLoading.set(false)))
      .subscribe({
        next: (settlements) => this.settlements.set(settlements),
        error: () => this.historyError.set('Your settlement history could not be loaded. Try again.'),
      });
  }

  protected selectSettlement(item: MerchantSettlement): void {
    this.settlement.set(item);
    this.lookupForm.controls.settlementId.setValue(item.id);
    this.settlementError.set(null);
  }

  protected loadLedger(page = 0): void {
    if (this.currencyForm.invalid) {
      this.currencyForm.markAllAsTouched();
      return;
    }
    const currency = this.currencyForm.controls.currency.value.toUpperCase();
    this.currency.set(currency);
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listLedger({ page, currency })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (ledger) => this.ledger.set(ledger),
        error: () => this.error.set('Your merchant ledger could not be loaded. Try again.'),
      });
  }

  protected findSettlement(): void {
    if (this.lookupForm.invalid || this.lookupBusy()) {
      this.lookupForm.markAllAsTouched();
      return;
    }
    this.lookupBusy.set(true);
    this.settlementError.set(null);
    this.settlement.set(null);
    this.api
      .getSettlement(this.lookupForm.controls.settlementId.value.trim())
      .pipe(finalize(() => this.lookupBusy.set(false)))
      .subscribe({
        next: (settlement) => this.settlement.set(settlement),
        error: (response: { error?: ProblemDetail; status?: number }) =>
          this.settlementError.set(
            response.error?.detail ||
              (response.status === 404
                ? 'No settlement was found with that ID.'
                : 'Settlement details could not be loaded.'),
          ),
      });
  }

  protected entryClass(amount: number): string {
    return amount < 0 ? 'sd-badge--warning' : 'sd-badge--success';
  }
  protected settlementClass(status: MerchantSettlement['status']): string {
    return status === 'PAID'
      ? 'sd-badge--success'
      : status === 'APPROVED'
        ? 'sd-badge--info'
        : 'sd-badge--warning';
  }
}
