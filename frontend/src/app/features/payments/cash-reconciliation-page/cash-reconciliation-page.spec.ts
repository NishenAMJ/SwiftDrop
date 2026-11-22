import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { CashHandover } from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';
import { CashReconciliationPage } from './cash-reconciliation-page';

function handover(overrides: Partial<CashHandover> = {}): CashHandover {
  return {
    id: 'h1', courierId: 'c1', collectionIds: ['x'], expectedAmount: 1000, submittedAmount: 950, currency: 'LKR',
    status: 'SUBMITTED', submittedAt: '2026-11-22T10:00:00Z', reconciliation: null, ...overrides,
  } as CashHandover;
}

describe('CashReconciliationPage', () => {
  function setup(list: () => Observable<unknown> = () => of({ content: [handover()], page: 2, size: 20, totalElements: 41, totalPages: 3 })) {
    const api = {
      listHandovers: vi.fn(list),
      confirmHandover: vi.fn((_id: string, _request: unknown, _key: string): Observable<CashHandover> =>
        of(handover({ status: 'CONFIRMED' }))),
    };
    TestBed.configureTestingModule({ imports: [CashReconciliationPage], providers: [{ provide: CodSettlementService, useValue: api }] });
    const fixture = TestBed.createComponent(CashReconciliationPage);
    fixture.detectChanges();
    return { page: fixture.componentInstance, api };
  }

  it('loads submitted handovers by default and applies the courier filter', () => {
    const { page, api } = setup();
    expect(api.listHandovers).toHaveBeenCalledWith({ page: 0, courierId: '', status: 'SUBMITTED' });
    page['filters'].setValue({ courierId: 'c9', status: 'FLAGGED' });
    page['load']();
    expect(api.listHandovers).toHaveBeenLastCalledWith({ page: 0, courierId: 'c9', status: 'FLAGGED' });
  });

  it('shows an error when handovers cannot be loaded', () => {
    const { page } = setup(() => throwError(() => ({ status: 500 })));
    expect(page['error']()).toContain('could not be loaded');
    expect(page['loading']()).toBe(false);
  });

  it('prefills the received amount with what the courier submitted', () => {
    const { page } = setup();
    page['select'](handover());
    expect(page['form'].getRawValue().receivedAmount).toBe('950.00');
  });

  it('requires an investigation note when the received amount differs from the expected amount', () => {
    const { page, api } = setup();
    page['select'](handover());
    page['confirm'](handover());
    expect(api.confirmHandover).not.toHaveBeenCalled();
    expect(page['form'].controls.investigationNote.hasError('requiredForDifference')).toBe(true);

    page['form'].patchValue({ investigationNote: '  Courier short by 50  ' });
    api.confirmHandover.mockReturnValueOnce(of(handover({ status: 'FLAGGED' })));
    page['confirm'](handover());
    expect(api.confirmHandover).toHaveBeenCalledWith('h1', { receivedAmount: 950, investigationNote: 'Courier short by 50' }, expect.any(String));
    expect(page['notice']()).toContain('flagged for investigation');
    expect(api.listHandovers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));
  });

  it('confirms an exact balance without a note', () => {
    const { page, api } = setup();
    page['select'](handover());
    page['form'].patchValue({ receivedAmount: '1000' });
    page['confirm'](handover());
    expect(api.confirmHandover).toHaveBeenCalledWith('h1', { receivedAmount: 1000 }, expect.any(String));
    expect(page['notice']()).toContain('exact balance');
  });

  it('rejects malformed amounts', () => {
    const { page, api } = setup();
    page['select'](handover());
    page['form'].patchValue({ receivedAmount: '-1' });
    page['confirm'](handover());
    page['form'].patchValue({ receivedAmount: 'abc' });
    page['confirm'](handover());
    expect(api.confirmHandover).not.toHaveBeenCalled();
  });

  it('keeps the idempotency key across retries and explains finalized handovers', () => {
    const { page, api } = setup();
    page['select'](handover());
    page['form'].patchValue({ receivedAmount: '1000' });
    api.confirmHandover.mockReturnValue(throwError(() => ({ status: 502 })));
    page['confirm'](handover());
    page['confirm'](handover());
    expect(api.confirmHandover.mock.calls[0][2]).toBe(api.confirmHandover.mock.calls[1][2]);
    expect(page['error']()).toBe('The reconciliation could not be recorded.');

    api.confirmHandover.mockReturnValue(throwError(() => ({ status: 409 })));
    page['confirm'](handover());
    expect(page['error']()).toContain('already been finalized');
  });
});
