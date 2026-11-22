import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { CancellationRefund, Payment, PaymentPage, Refund } from '../payment.models';
import { PaymentService } from '../payment.service';
import { PaymentHistoryPage } from './payment-history-page';

const REFUND_ID = '3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';

function payment(overrides: Partial<Payment> = {}): Payment {
  return {
    id: 'pay-1',
    bookingId: 'SWD-0001',
    amount: 1500,
    currency: 'LKR',
    status: 'SUCCESS',
    refundedAmount: 0,
    provider: 'PAYHERE',
    providerReference: '320025',
    createdAt: '2026-11-20T10:00:00Z',
    updatedAt: '2026-11-20T10:00:00Z',
    ...overrides,
  };
}

function refund(overrides: Partial<Refund> = {}): Refund {
  return {
    id: REFUND_ID,
    paymentId: 'pay-1',
    amount: 500,
    currency: 'LKR',
    status: 'PENDING',
    createdAt: '2026-11-20T10:05:00Z',
    updatedAt: '2026-11-20T10:05:00Z',
    ...overrides,
  };
}

/** A refund mock whose 4th argument is the idempotency key. */
function refundMock(result: () => Observable<Refund>) {
  return vi.fn((_id: string, _amount: number, _reason: string, _key: string) => result());
}

function pageOf(items: Payment[]): PaymentPage {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

function setup(roles: string[], overrides: Partial<Record<keyof PaymentService, unknown>> = {}) {
  const api = {
    history: vi.fn((): Observable<PaymentPage> => of(pageOf([payment()]))),
    cancellations: vi.fn(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    get: vi.fn((id: string) => of(payment({ id }))),
    refund: vi.fn((): Observable<Refund> => of(refund())),
    refundStatus: vi.fn((): Observable<Refund> => of(refund())),
    retryRefund: vi.fn((): Observable<Refund> => of(refund())),
    resolveRefund: vi.fn((): Observable<Refund> => of(refund({ status: 'SUCCESS' }))),
    releaseBookingClaim: vi.fn((): Observable<void> => of(undefined)),
    recoveryAudit: vi.fn(() => of([])),
    ...overrides,
  };
  TestBed.configureTestingModule({
    imports: [PaymentHistoryPage],
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { roles: () => roles } },
      { provide: PaymentService, useValue: api },
    ],
  });
  const fixture = TestBed.createComponent(PaymentHistoryPage);
  fixture.detectChanges();
  return { fixture, page: fixture.componentInstance, api };
}

describe('PaymentHistoryPage loading', () => {
  it('shows a safe message when history fails and never leaks the server error', () => {
    const { fixture, page } = setup(['CUSTOMER'], {
      history: vi.fn(() => throwError(() => new Error('SQL timeout on payments_db'))),
    });
    expect(page.loading()).toBe(false);
    expect(page.error()).toContain('Payment history could not be loaded');
    expect(fixture.nativeElement.textContent).not.toContain('SQL timeout');
  });

  it('keeps the page index returned by the server', () => {
    const { page, api } = setup(['CUSTOMER'], {
      history: vi.fn(() => of({ ...pageOf([payment()]), page: 3, totalPages: 5 })),
    });
    expect(api.history).toHaveBeenCalledWith(0);
    expect(page.pageIndex()).toBe(3);
    expect(page.totalPages()).toBe(5);
  });
});

describe('PaymentHistoryPage selecting payments', () => {
  it('toggles the selection when the same payment is opened twice', () => {
    const { page } = setup(['CUSTOMER']);
    page.openPayment(payment());
    expect(page.selectedPayment()?.id).toBe('pay-1');
    page.openPayment(payment());
    expect(page.selectedPayment()).toBeNull();
  });

  it('loads the recovery audit only for dispatchers', () => {
    const customer = setup(['CUSTOMER']);
    customer.page.openPayment(payment());
    expect(customer.api.recoveryAudit).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    const dispatcher = setup(['DISPATCHER']);
    dispatcher.page.openPayment(payment());
    expect(dispatcher.api.recoveryAudit).toHaveBeenCalledWith('pay-1');
  });

  it('clears the audit when it cannot be loaded', () => {
    const { page } = setup(['DISPATCHER'], { recoveryAudit: vi.fn(() => throwError(() => ({ status: 500 }))) });
    page.openPayment(payment());
    expect(page.audit()).toEqual([]);
  });

  it('only allows dispatchers to refund successful or partially refunded payments', () => {
    const customer = setup(['CUSTOMER']).page;
    expect(customer.canRefund(payment())).toBe(false);
    TestBed.resetTestingModule();
    const dispatcher = setup(['DISPATCHER']).page;
    expect(dispatcher.canRefund(payment({ status: 'SUCCESS' }))).toBe(true);
    expect(dispatcher.canRefund(payment({ status: 'PARTIALLY_REFUNDED' }))).toBe(true);
    for (const status of ['PENDING', 'FAILED', 'REFUNDED', 'CHARGEBACKED'] as const) {
      expect(dispatcher.canRefund(payment({ status }))).toBe(false);
    }
  });
});

describe('PaymentHistoryPage refunds', () => {
  function withSelected(overrides: Partial<Record<keyof PaymentService, unknown>> = {}) {
    const ctx = setup(['DISPATCHER'], overrides);
    ctx.page.openPayment(payment());
    return ctx;
  }

  it('does not submit an invalid refund amount', () => {
    const { page, api } = withSelected();
    for (const amount of ['', '0', '-5', '12.345', 'abc']) {
      page.refundForm.setValue({ amount, reason: 'Customer request' });
      page.createRefund();
    }
    expect(api.refund).not.toHaveBeenCalled();
    expect(page.refundForm.touched).toBe(true);
  });

  it('reuses the idempotency key when the same refund is retried after a failure', () => {
    const refundCall = refundMock(() => throwError(() => ({ status: 502 })));
    const { page } = withSelected({ refund: refundCall });
    page.refundForm.setValue({ amount: '500', reason: 'Damaged parcel' });
    page.createRefund();
    page.createRefund();
    expect(refundCall).toHaveBeenCalledTimes(2);
    const firstKey = refundCall.mock.calls[0]![3];
    const secondKey = refundCall.mock.calls[1]![3];
    expect(firstKey).toBeTruthy();
    expect(secondKey).toBe(firstKey);
    expect(page.actionError()).toContain('could not be completed');
  });

  it('uses a new idempotency key when the refund details change', () => {
    const refundCall = refundMock(() => throwError(() => ({ status: 500 })));
    const { page } = withSelected({ refund: refundCall });
    page.refundForm.setValue({ amount: '500', reason: 'Damaged parcel' });
    page.createRefund();
    page.refundForm.setValue({ amount: '400', reason: 'Damaged parcel' });
    page.createRefund();
    expect(refundCall.mock.calls[0]![3]).not.toBe(refundCall.mock.calls[1]![3]);
  });

  it('stores the returned refund, refreshes the payment and starts a fresh key afterwards', () => {
    const refundCall = refundMock(() => of(refund()));
    const { page, api } = withSelected({ refund: refundCall });
    page.refundForm.setValue({ amount: '500', reason: 'Damaged parcel' });
    page.createRefund();
    expect(page.currentRefund()?.id).toBe(REFUND_ID);
    expect(page.refundLookupForm.controls.refundId.value).toBe(REFUND_ID);
    expect(page.message()).toContain('Refund request submitted');
    expect(api.get).toHaveBeenCalledWith('pay-1');

    page.createRefund();
    expect(refundCall.mock.calls[1]![3]).not.toBe(refundCall.mock.calls[0]![3]);
  });

  it.each([
    [409, 'payment state changed'],
    [403, 'not allowed to perform this payment action'],
    [500, 'could not be completed'],
  ])('maps HTTP %s to a safe action message', (status, text) => {
    const { page } = withSelected({ refund: vi.fn(() => throwError(() => ({ status, error: { detail: 'secret' } }))) });
    page.refundForm.setValue({ amount: '1', reason: 'x' });
    page.createRefund();
    expect(page.actionError()).toContain(text);
    expect(page.actionError()).not.toContain('secret');
    expect(page.busy()).toBe(false);
  });

  it('rejects malformed refund ids without calling the API', () => {
    const { page, api } = withSelected();
    page.refundLookupForm.setValue({ refundId: 'not-a-uuid' });
    page.lookupRefund();
    expect(api.refundStatus).not.toHaveBeenCalled();
  });

  it('reports a refund that does not belong to the payment', () => {
    const { page } = withSelected({ refundStatus: vi.fn(() => throwError(() => ({ status: 404 }))) });
    page.refundLookupForm.setValue({ refundId: REFUND_ID });
    page.lookupRefund();
    expect(page.refundLookupError()).toContain('could not be found');
    expect(page.busy()).toBe(false);
  });

  it('refreshes the current refund and reports refresh failures', () => {
    const status = vi.fn((): Observable<Refund> => of(refund({ status: 'SUCCESS' })));
    const { page } = withSelected({ refundStatus: status });
    page.refreshRefund();
    expect(status).not.toHaveBeenCalled();

    page.currentRefund.set(refund());
    page.refreshRefund();
    expect(page.currentRefund()?.status).toBe('SUCCESS');

    status.mockReturnValueOnce(throwError(() => ({ status: 500 })));
    page.refreshRefund();
    expect(page.actionError()).toContain('could not be refreshed');
  });

  it('only retries a pending refund after the operator confirms the provider has no refund', () => {
    const { page, api } = withSelected();
    page.currentRefund.set(refund());
    page.retryRefund();
    expect(api.retryRefund).not.toHaveBeenCalled();

    page.retryForm.setValue({ confirmNoRefundAtProvider: true });
    page.currentRefund.set(refund({ status: 'SUCCESS' }));
    page.retryRefund();
    expect(api.retryRefund).not.toHaveBeenCalled();

    page.currentRefund.set(refund());
    page.retryRefund();
    expect(api.retryRefund).toHaveBeenCalledWith('pay-1', REFUND_ID);
  });

  it('requires a provider refund reference when confirming a successful refund', () => {
    const { page, api } = withSelected();
    page.currentRefund.set(refund());
    page.resolutionForm.setValue({
      resolution: 'CONFIRMED_SUCCESS',
      evidenceType: 'PAYHERE_DASHBOARD',
      evidenceReference: 'dash-123',
      providerRefundReference: '',
      reason: 'Seen in dashboard',
      confirmProviderEvidence: true,
    });
    page.resolveRefund();
    expect(api.resolveRefund).not.toHaveBeenCalled();
    expect(page.resolutionForm.controls.providerRefundReference.hasError('required')).toBe(true);
  });

  it('sends trimmed evidence and drops the provider reference for confirmed failures', () => {
    const { page, api } = withSelected();
    page.currentRefund.set(refund());
    page.resolutionForm.patchValue({ resolution: 'CONFIRMED_FAILURE', providerRefundReference: '999' });
    page.onResolutionChange();
    expect(page.resolutionForm.controls.providerRefundReference.value).toBe('');
    page.resolutionForm.patchValue({
      evidenceType: 'PAYHERE_SUPPORT_CASE',
      evidenceReference: '  case-77  ',
      reason: '  Provider rejected  ',
      confirmProviderEvidence: true,
    });
    page.resolveRefund();
    expect(api.resolveRefund).toHaveBeenCalledWith('pay-1', REFUND_ID, {
      resolution: 'CONFIRMED_FAILURE',
      evidenceType: 'PAYHERE_SUPPORT_CASE',
      evidenceReference: 'case-77',
      providerRefundReference: undefined,
      reason: 'Provider rejected',
      confirmProviderEvidence: true,
    });
    expect(page.message()).toContain('Refund outcome recorded');
  });

  it('makes the provider reference required again when switching back to success', () => {
    const { page } = withSelected();
    page.resolutionForm.patchValue({ resolution: 'CONFIRMED_FAILURE' });
    page.onResolutionChange();
    page.resolutionForm.patchValue({ resolution: 'CONFIRMED_SUCCESS' });
    page.onResolutionChange();
    expect(page.resolutionForm.controls.providerRefundReference.hasError('required')).toBe(true);
  });
});

describe('PaymentHistoryPage booking claim release', () => {
  it('is refused for payments that are not FAILED', () => {
    const { page, api } = setup(['DISPATCHER']);
    page.openPayment(payment({ status: 'SUCCESS' }));
    page.releaseForm.setValue({
      evidenceType: 'PAYHERE_DASHBOARD',
      evidenceReference: 'dash-1',
      reason: 'Terminal failure',
      confirmProviderTerminalFailure: true,
    });
    page.releaseBookingClaim();
    expect(api.releaseBookingClaim).not.toHaveBeenCalled();
  });

  it('requires explicit confirmation and then records the release', () => {
    const { page, api } = setup(['DISPATCHER']);
    page.openPayment(payment({ status: 'FAILED' }));
    page.releaseForm.patchValue({ evidenceReference: ' dash-1 ', reason: ' Terminal failure ' });
    page.releaseBookingClaim();
    expect(api.releaseBookingClaim).not.toHaveBeenCalled();

    page.releaseForm.patchValue({ confirmProviderTerminalFailure: true });
    page.releaseBookingClaim();
    expect(api.releaseBookingClaim).toHaveBeenCalledWith('pay-1', {
      confirmProviderTerminalFailure: true,
      evidenceType: 'PAYHERE_DASHBOARD',
      evidenceReference: 'dash-1',
      reason: 'Terminal failure',
    });
    expect(page.message()).toContain('claim release recorded');
  });
});

describe('PaymentHistoryPage cancellation refunds', () => {
  const row: CancellationRefund = {
    eventId: 'evt-1',
    bookingId: 'SWD-0001',
    paymentId: 'pay-1',
    refundId: REFUND_ID,
    status: 'PENDING',
    failureReason: null,
    createdAt: '2026-11-20T10:00:00Z',
    updatedAt: '2026-11-20T10:00:00Z',
  };

  it('opens the payment and looks up the linked refund', () => {
    const { page, api } = setup(['DISPATCHER']);
    page.openCancellation(row);
    expect(api.get).toHaveBeenCalledWith('pay-1');
    expect(page.selectedPayment()?.id).toBe('pay-1');
    expect(api.refundStatus).toHaveBeenCalledWith('pay-1', REFUND_ID);
    expect(page.currentRefund()?.id).toBe(REFUND_ID);
  });

  it('ignores rows without a payment and reports payments that cannot be loaded', () => {
    const { page, api } = setup(['DISPATCHER'], { get: vi.fn(() => throwError(() => ({ status: 404 }))) });
    page.openCancellation({ ...row, paymentId: null });
    expect(api.get).not.toHaveBeenCalled();
    page.openCancellation(row);
    expect(page.cancellationsError()).toContain('payment could not be loaded');
  });
});
