import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { CashHandover } from '../cod-settlement.models';
import { CodSettlementService } from '../cod-settlement.service';
import { CourierCashPage } from './courier-cash-page';

const A = '3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const B = '4f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';

function handover(overrides: Partial<CashHandover> = {}): CashHandover {
  return {
    id: 'h1', courierId: 'c1', collectionIds: [A], expectedAmount: 1000, submittedAmount: 1000, currency: 'LKR',
    status: 'SUBMITTED', submittedAt: '2026-11-22T10:00:00Z', reconciliation: null, ...overrides,
  } as CashHandover;
}

describe('CourierCashPage', () => {
  function setup(list: () => Observable<unknown> = () => of({ content: [handover()], page: 0, size: 20, totalElements: 1, totalPages: 1 })) {
    const api = {
      listHandovers: vi.fn(list),
      createHandover: vi.fn((_request: unknown, _key: string): Observable<CashHandover> => of(handover({ id: 'h-new' }))),
    };
    TestBed.configureTestingModule({ imports: [CourierCashPage], providers: [{ provide: CodSettlementService, useValue: api }] });
    const fixture = TestBed.createComponent(CourierCashPage);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, api, text: () => fixture.nativeElement.textContent as string };
  }

  it('loads the courier handover history', () => {
    const { page, api } = setup();
    expect(api.listHandovers).toHaveBeenCalledWith({ page: 0 });
    expect(page['loading']()).toBe(false);
    expect(page['result']()?.content).toHaveLength(1);
  });

  it('shows a retryable message when history fails', () => {
    const { page } = setup(() => throwError(() => ({ status: 500 })));
    expect(page['error']()).toContain('could not be loaded');
    expect(page['loading']()).toBe(false);
  });

  it.each([
    ['not-a-uuid', '100'],
    [A, ''],
    [A, '0'],
    [A, '10.555'],
  ])('rejects invalid collection ids or amounts (%s, %s)', (ids, amount) => {
    const { page, api } = setup();
    page['form'].setValue({ collectionIds: ids, submittedAmount: amount, currency: 'LKR' });
    page['submit']();
    expect(api.createHandover).not.toHaveBeenCalled();
  });

  it('accepts comma or newline separated ids and removes duplicates', () => {
    const { page, api } = setup();
    page['form'].setValue({ collectionIds: `${A},\n${B}, ${A}`, submittedAmount: '2500.50', currency: 'LKR' });
    page['submit']();
    expect(api.createHandover).toHaveBeenCalledWith({ collectionIds: [A, B], submittedAmount: 2500.5, currency: 'LKR' }, expect.any(String));
    expect(page['notice']()).toContain('h-new');
    expect(page['form'].getRawValue().collectionIds).toBe('');
  });

  it('reuses the idempotency key when the same handover is retried after a failure', () => {
    const { page, api } = setup();
    api.createHandover.mockReturnValue(throwError(() => ({ status: 503 })));
    page['form'].setValue({ collectionIds: A, submittedAmount: '1000', currency: 'LKR' });
    page['submit']();
    page['submit']();
    expect(api.createHandover.mock.calls[0][1]).toBe(api.createHandover.mock.calls[1][1]);
    expect(page['error']()).toContain('could not be submitted');
  });

  it('explains conflicts and shows the server problem detail when given', () => {
    const { page, api } = setup();
    page['form'].setValue({ collectionIds: A, submittedAmount: '1000', currency: 'LKR' });
    api.createHandover.mockReturnValueOnce(throwError(() => ({ status: 409 })));
    page['submit']();
    expect(page['error']()).toContain('already assigned');
    api.createHandover.mockReturnValueOnce(throwError(() => ({ status: 400, error: { detail: 'collectionIds must not contain duplicates.' } })));
    page['submit']();
    expect(page['error']()).toBe('collectionIds must not contain duplicates.');
  });

  it('toggles the selected handover and maps statuses to text badges', () => {
    const { page } = setup();
    page['toggle'](handover());
    expect(page['selected']()?.id).toBe('h1');
    page['toggle'](handover());
    expect(page['selected']()).toBeNull();
    expect(page['statusClass']('CONFIRMED')).toBe('sd-badge--success');
    expect(page['statusClass']('FLAGGED')).toBe('sd-badge--danger');
    expect(page['statusClass']('SUBMITTED')).toBe('sd-badge--warning');
  });
});
