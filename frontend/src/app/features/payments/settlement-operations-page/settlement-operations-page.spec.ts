import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { MerchantSettlement } from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';
import { SettlementOperationsPage } from './settlement-operations-page';

const MERCHANT = '846c790e-497a-4340-8039-79e25c95f40e';
const SETTLEMENT = '3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';

function settlement(overrides: Partial<MerchantSettlement> = {}): MerchantSettlement {
  return { id: SETTLEMENT, merchantId: MERCHANT, amount: 900, currency: 'LKR', status: 'CALCULATED', ...overrides } as MerchantSettlement;
}

describe('SettlementOperationsPage', () => {
  function setup() {
    const page0 = { content: [settlement()], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    const api = {
      listLedger: vi.fn(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, balance: 0, currency: 'LKR' })),
      createAdjustment: vi.fn((_r: unknown, _key: string): Observable<unknown> => of({})),
      calculateSettlement: vi.fn((_r: unknown, _key: string): Observable<MerchantSettlement> => of(settlement())),
      getSettlement: vi.fn((): Observable<MerchantSettlement> => of(settlement())),
      listSettlements: vi.fn(() => of(page0)),
      approveSettlement: vi.fn((_id: string, _note: string, _key: string): Observable<MerchantSettlement> =>
        of(settlement({ status: 'APPROVED' }))),
      recordSettlementPayment: vi.fn((_id: string, _r: unknown, _key: string): Observable<MerchantSettlement> =>
        of(settlement({ status: 'PAID' }))),
    };
    TestBed.configureTestingModule({ imports: [SettlementOperationsPage], providers: [{ provide: CodSettlementService, useValue: api }] });
    const fixture = TestBed.createComponent(SettlementOperationsPage);
    fixture.detectChanges();
    return { page: fixture.componentInstance, api };
  }

  it('prefills cutoff and payment times with the current local time', () => {
    const { page } = setup();
    expect(page['calculateForm'].controls.cutoffAt.value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(page['paymentForm'].controls.paidAt.value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });

  it('only loads a ledger for a valid merchant id and normalises the currency', () => {
    const { page, api } = setup();
    page['loadLedger']();
    expect(api.listLedger).not.toHaveBeenCalled();
    page['ledgerForm'].setValue({ merchantId: MERCHANT, currency: 'LKR' });
    page['loadLedger'](2);
    expect(api.listLedger).toHaveBeenCalledWith({ merchantId: MERCHANT, currency: 'LKR', page: 2 });
  });

  it('shows the fallback or the server problem when the ledger fails', () => {
    const { page, api } = setup();
    page['ledgerForm'].setValue({ merchantId: MERCHANT, currency: 'LKR' });
    api.listLedger.mockReturnValueOnce(throwError(() => ({ status: 500 })) as never);
    page['loadLedger']();
    expect(page['error']()).toBe('Merchant ledger could not be loaded.');
    api.listLedger.mockReturnValueOnce(throwError(() => ({ status: 409 })) as never);
    page['loadLedger']();
    expect(page['error']()).toContain('Refresh it before continuing');
  });

  it('rejects a zero adjustment and posts a signed one with an idempotency key', () => {
    const { page, api } = setup();
    page['adjustmentForm'].setValue({ merchantId: MERCHANT, amount: '0', currency: 'LKR', sourceReference: 'case-1', reason: 'Fix' });
    page['createAdjustment']();
    expect(api.createAdjustment).not.toHaveBeenCalled();
    expect(page['adjustmentForm'].controls.amount.hasError('nonZero')).toBe(true);

    page['adjustmentForm'].setValue({ merchantId: MERCHANT, amount: '-100.50', currency: 'LKR', sourceReference: ' case-1 ', reason: ' Fix ' });
    page['createAdjustment']();
    expect(api.createAdjustment).toHaveBeenCalledWith(
      { merchantId: MERCHANT, amount: -100.5, currency: 'LKR', sourceReference: 'case-1', reason: 'Fix' }, expect.any(String));
    expect(page['notice']()).toContain('Ledger adjustment posted');
    expect(api.listLedger).toHaveBeenCalledWith({ merchantId: MERCHANT, currency: 'LKR', page: 0 });
    expect(page['notice']()).toContain('Ledger adjustment posted');
  });

  it('reuses the idempotency key for a retried calculation and a new one after success', () => {
    const { page, api } = setup();
    page['calculateForm'].setValue({ merchantId: MERCHANT, currency: 'LKR', cutoffAt: '2026-11-22T10:00' });
    api.calculateSettlement.mockReturnValueOnce(throwError(() => ({ status: 502 })));
    page['calculate']();
    page['calculate']();
    const [first, second] = api.calculateSettlement.mock.calls.map((call) => call[1]);
    expect(second).toBe(first);
    expect(page['settlement']()?.id).toBe(SETTLEMENT);
    expect(page['lookupForm'].controls.settlementId.value).toBe(SETTLEMENT);
    expect(api.listSettlements).toHaveBeenCalledWith({ merchantId: MERCHANT, page: 0 });

    page['calculate']();
    expect(api.calculateSettlement.mock.calls[2][1]).not.toBe(first);
  });

  it('does not calculate with an invalid merchant or missing cutoff', () => {
    const { page, api } = setup();
    page['calculateForm'].setValue({ merchantId: 'not-a-uuid', currency: 'LKR', cutoffAt: '2026-11-22T10:00' });
    page['calculate']();
    page['calculateForm'].setValue({ merchantId: MERCHANT, currency: 'LKR', cutoffAt: '' });
    page['calculate']();
    expect(api.calculateSettlement).not.toHaveBeenCalled();
  });

  it('finds a settlement by id and reports unknown ids', () => {
    const { page, api } = setup();
    page['lookupForm'].setValue({ settlementId: SETTLEMENT });
    page['findSettlement']();
    expect(page['settlement']()?.id).toBe(SETTLEMENT);
    api.getSettlement.mockReturnValueOnce(throwError(() => ({ status: 404 })));
    page['findSettlement']();
    expect(page['error']()).toBe('Settlement could not be found.');
  });

  it('approval needs explicit confirmation and updates the history row', () => {
    const { page, api } = setup();
    page['settlementHistoryForm'].setValue({ merchantId: MERCHANT });
    page['loadSettlementHistory']();
    page['selectSettlement'](settlement());
    page['approve']();
    expect(api.approveSettlement).not.toHaveBeenCalled();

    page['approvalForm'].setValue({ note: ' Ledger checked ', confirm: true });
    page['approve']();
    expect(api.approveSettlement).toHaveBeenCalledWith(SETTLEMENT, 'Ledger checked', expect.any(String));
    expect(page['settlement']()?.status).toBe('APPROVED');
    expect(page['settlements']()?.content[0].status).toBe('APPROVED');
    expect(page['notice']()).toContain('No payment has been recorded yet');
  });

  it('records an external payout only with confirmation and a reference', () => {
    const { page, api } = setup();
    page['selectSettlement'](settlement({ status: 'APPROVED' }));
    page['paymentForm'].patchValue({ externalReference: 'bank-123', confirm: false });
    page['recordPayment']();
    expect(api.recordSettlementPayment).not.toHaveBeenCalled();

    page['paymentForm'].patchValue({ externalReference: ' bank-123 ', paidAt: '2026-11-22T10:00', confirm: true });
    page['recordPayment']();
    expect(api.recordSettlementPayment).toHaveBeenCalledWith(SETTLEMENT,
      { externalReference: 'bank-123', paidAt: new Date('2026-11-22T10:00').toISOString() }, expect.any(String));
    expect(page['settlement']()?.status).toBe('PAID');
  });

  it('cannot approve or pay without a selected settlement', () => {
    const { page, api } = setup();
    page['approvalForm'].setValue({ note: '', confirm: true });
    page['approve']();
    page['recordPayment']();
    expect(api.approveSettlement).not.toHaveBeenCalled();
    expect(api.recordSettlementPayment).not.toHaveBeenCalled();
  });

  it('reports settlement history failures and resets paging for a new merchant', () => {
    const { page, api } = setup();
    page['settlementHistoryForm'].setValue({ merchantId: MERCHANT });
    page['loadSettlementHistory'](3);
    expect(api.listSettlements).toHaveBeenLastCalledWith({ merchantId: MERCHANT, page: 0 });
    page['loadSettlementHistory'](3);
    expect(api.listSettlements).toHaveBeenLastCalledWith({ merchantId: MERCHANT, page: 3 });

    api.listSettlements.mockReturnValueOnce(throwError(() => ({ status: 500 })) as never);
    page['loadSettlementHistory']();
    expect(page['historyError']()).toContain('could not be loaded');
  });

  it('switches operations, clears messages and labels statuses with text badges', () => {
    const { page } = setup();
    page['error'].set('old');
    page['show']('settlement');
    expect(page['operation']()).toBe('settlement');
    expect(page['error']()).toBeNull();
    expect(page['settlementClass']('PAID')).toBe('sd-badge--success');
    expect(page['settlementClass']('APPROVED')).toBe('sd-badge--info');
    expect(page['settlementClass']('CALCULATED')).toBe('sd-badge--warning');
  });
});
