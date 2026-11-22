import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { PaymentService } from '../payment.service';
import { CancellationRefund, CancellationRefundPage, EvidenceType, Payment, PaymentPage, RecoveryAudit, Refund } from '../payment.models';

@Component({
  selector: 'app-payment-history-page',
  imports: [CommonModule, DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './payment-history-page.html',
  styleUrl: './payment-history-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentHistoryPage implements OnInit {
  private readonly api = inject(PaymentService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly dispatcher = this.auth.roles().includes('DISPATCHER');
  readonly result = signal<PaymentPage | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly pageIndex = signal(0);
  readonly selectedPayment = signal<Payment | null>(null);
  readonly audit = signal<RecoveryAudit[]>([]);
  readonly currentRefund = signal<Refund | null>(null);
  readonly refundLookupError = signal<string | null>(null);
  readonly busy = signal(false);
  private refundAttempt: { fingerprint: string; key: string } | null = null;
  readonly visiblePayments = computed(() => {
    const items = this.result()?.content ?? [];
    const selected = this.selectedPayment();
    return selected && !items.some(item => item.id === selected.id) ? [selected, ...items] : items;
  });
  readonly totalPages = computed(() => this.result()?.totalPages ?? 0);

  readonly refundForm = this.fb.nonNullable.group({
    amount: ['', [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/), Validators.min(0.01)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });
  readonly refundLookupForm = this.fb.nonNullable.group({
    refundId: ['', [Validators.required, Validators.pattern(/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i)]],
  });
  readonly resolutionForm = this.fb.nonNullable.group({
    resolution: ['CONFIRMED_SUCCESS' as 'CONFIRMED_SUCCESS' | 'CONFIRMED_FAILURE', Validators.required],
    evidenceType: ['PAYHERE_DASHBOARD' as EvidenceType, Validators.required],
    evidenceReference: ['', [Validators.required, Validators.maxLength(200)]],
    providerRefundReference: ['', [Validators.pattern(/^\d{1,30}$/)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    confirmProviderEvidence: [false, Validators.requiredTrue],
  });
  readonly retryForm = this.fb.nonNullable.group({
    confirmNoRefundAtProvider: [false, Validators.requiredTrue],
  });
  readonly releaseForm = this.fb.nonNullable.group({
    evidenceType: ['PAYHERE_DASHBOARD' as EvidenceType, Validators.required],
    evidenceReference: ['', [Validators.required, Validators.maxLength(200)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    confirmProviderTerminalFailure: [false, Validators.requiredTrue],
  });

  readonly cancellations = signal<CancellationRefundPage | null>(null);
  readonly cancellationsLoading = signal(false);
  readonly cancellationsError = signal<string | null>(null);

  loadCancellations(page = 0): void {
    if (!this.dispatcher) return;
    this.cancellationsLoading.set(true);
    this.cancellationsError.set(null);
    this.api.cancellations(page).subscribe({
      next: result => { this.cancellations.set(result); this.cancellationsLoading.set(false); },
      error: () => { this.cancellationsError.set('Cancellation refunds could not be loaded. Try again.'); this.cancellationsLoading.set(false); },
    });
  }

  openCancellation(row: CancellationRefund): void {
    if (!row.paymentId) return;
    this.api.get(row.paymentId).subscribe({
      next: payment => {
        this.selectedPayment.set(null);
        this.openPayment(payment);
        if (row.refundId) {
          this.refundLookupForm.controls.refundId.setValue(row.refundId);
          this.lookupRefund();
        }
      },
      error: () => this.cancellationsError.set('The payment could not be loaded. Try again.'),
    });
  }

  ngOnInit(): void {
    this.loadCancellations();
    this.load();
  }

  load(page = this.pageIndex()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.history(page).subscribe({
      next: (result) => {
        this.result.set(result);
        this.pageIndex.set(result.page);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Payment history could not be loaded. Check your connection and try again.');
        this.loading.set(false);
      },
    });
  }

  openPayment(payment: Payment): void {
    if (this.selectedPayment()?.id === payment.id) {
      this.selectedPayment.set(null);
      return;
    }
    this.selectedPayment.set(payment);
    this.currentRefund.set(null);
    this.audit.set([]);
    this.actionError.set(null);
    this.message.set(null);
    this.refundLookupError.set(null);
    this.refundForm.reset();
    this.refundLookupForm.reset();
    this.resolutionForm.reset({
      resolution: 'CONFIRMED_SUCCESS', evidenceType: 'PAYHERE_DASHBOARD',
      evidenceReference: '', providerRefundReference: '', reason: '', confirmProviderEvidence: false,
    });
    this.retryForm.reset({ confirmNoRefundAtProvider: false });
    this.releaseForm.reset({
      evidenceType: 'PAYHERE_DASHBOARD', evidenceReference: '', reason: '',
      confirmProviderTerminalFailure: false,
    });
    if (this.dispatcher) {
      this.api.recoveryAudit(payment.id).subscribe({ next: (audit) => this.audit.set(audit), error: () => this.audit.set([]) });
    }
  }

  canRefund(payment: Payment): boolean {
    return this.dispatcher && ['SUCCESS', 'PARTIALLY_REFUNDED'].includes(payment.status);
  }

  createRefund(): void {
    const payment = this.selectedPayment();
    if (!payment || this.refundForm.invalid || this.busy()) {
      this.refundForm.markAllAsTouched();
      return;
    }
    const amount = Number(this.refundForm.controls.amount.value);
    const reason = this.refundForm.controls.reason.value.trim();
    const fingerprint = `${payment.id}|${amount.toFixed(2)}|${reason}`;
    if (!this.refundAttempt || this.refundAttempt.fingerprint !== fingerprint) {
      this.refundAttempt = { fingerprint, key: crypto.randomUUID() };
    }
    this.runAction(() => this.api.refund(
      payment.id,
      amount,
      reason,
      this.refundAttempt!.key,
    ), 'Refund request submitted. Check the returned state before taking another action.', true);
  }

  lookupRefund(): void {
    const payment = this.selectedPayment();
    if (!payment || this.refundLookupForm.invalid) {
      this.refundLookupForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.refundLookupError.set(null);
    this.api.refundStatus(payment.id, this.refundLookupForm.controls.refundId.value.trim()).subscribe({
      next: (refund) => { this.currentRefund.set(refund); this.busy.set(false); },
      error: () => { this.refundLookupError.set('That refund could not be found for this payment.'); this.busy.set(false); },
    });
  }

  refreshRefund(): void {
    const payment = this.selectedPayment();
    const refund = this.currentRefund();
    if (!payment || !refund || this.busy()) return;
    this.busy.set(true);
    this.api.refundStatus(payment.id, refund.id).subscribe({
      next: (updated) => { this.currentRefund.set(updated); this.busy.set(false); },
      error: () => { this.actionError.set('Refund status could not be refreshed.'); this.busy.set(false); },
    });
  }

  retryRefund(): void {
    const payment = this.selectedPayment();
    const refund = this.currentRefund();
    if (!payment || !refund || refund.status !== 'PENDING' || this.retryForm.invalid || this.busy()) {
      this.retryForm.markAllAsTouched();
      return;
    }
    this.runAction(() => this.api.retryRefund(payment.id, refund.id), 'Refund recovery request submitted.', true);
  }

  resolveRefund(): void {
    const payment = this.selectedPayment();
    const refund = this.currentRefund();
    if (!payment || !refund || refund.status !== 'PENDING' || this.resolutionForm.invalid || this.busy()) {
      this.resolutionForm.markAllAsTouched();
      return;
    }
    const value = this.resolutionForm.getRawValue();
    if (value.resolution === 'CONFIRMED_SUCCESS' && !value.providerRefundReference.trim()) {
      this.resolutionForm.controls.providerRefundReference.setErrors({ required: true });
      return;
    }
    this.runAction(() => this.api.resolveRefund(payment.id, refund.id, {
      resolution: value.resolution,
      evidenceType: value.evidenceType,
      evidenceReference: value.evidenceReference.trim(),
      providerRefundReference: value.resolution === 'CONFIRMED_SUCCESS' ? value.providerRefundReference.trim() : undefined,
      reason: value.reason.trim(),
      confirmProviderEvidence: true,
    }), 'Refund outcome recorded in the recovery audit.', true);
  }

  releaseBookingClaim(): void {
    const payment = this.selectedPayment();
    if (!payment || payment.status !== 'FAILED' || this.releaseForm.invalid || this.busy()) {
      this.releaseForm.markAllAsTouched();
      return;
    }
    const value = this.releaseForm.getRawValue();
    this.runAction(() => this.api.releaseBookingClaim(payment.id, {
      confirmProviderTerminalFailure: true,
      evidenceType: value.evidenceType,
      evidenceReference: value.evidenceReference.trim(),
      reason: value.reason.trim(),
    }), 'Booking payment claim release recorded.', false);
  }

  onResolutionChange(): void {
    const reference = this.resolutionForm.controls.providerRefundReference;
    if (this.resolutionForm.controls.resolution.value === 'CONFIRMED_SUCCESS') {
      reference.addValidators(Validators.required);
    } else {
      reference.removeValidators(Validators.required);
      reference.setValue('');
    }
    reference.updateValueAndValidity();
  }

  private runAction<T>(request: () => import('rxjs').Observable<T>, success: string, hasRefund: boolean): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.message.set(null);
    request().subscribe({
      next: (result) => {
        if (hasRefund && this.isRefund(result)) {
          this.currentRefund.set(result);
          this.refundLookupForm.controls.refundId.setValue(result.id);
          this.refundAttempt = null;
        }
        this.message.set(success);
        this.busy.set(false);
        if (this.selectedPayment()) {
          this.api.get(this.selectedPayment()!.id).subscribe({
            next: (payment) => {
              this.selectedPayment.set(payment);
              this.result.update((current) => current ? {
                ...current,
                content: current.content.map((item) => item.id === payment.id ? payment : item),
              } : current);
              if (this.dispatcher) this.api.recoveryAudit(payment.id).subscribe({ next: (audit) => this.audit.set(audit) });
            },
          });
        }
      },
      error: (error: { status?: number }) => {
        this.actionError.set(error?.status === 409
          ? 'The payment state changed or the request conflicts with an existing operation. Refresh payment details and review the refund state.'
          : error?.status === 403
            ? 'Your account is not allowed to perform this payment action.'
            : 'The payment action could not be completed. Check the payment and provider status before retrying.');
        this.busy.set(false);
      },
    });
  }

  private isRefund(value: unknown): value is Refund {
    return !!value && typeof value === 'object' && 'paymentId' in value && 'amount' in value && 'status' in value;
  }
}
