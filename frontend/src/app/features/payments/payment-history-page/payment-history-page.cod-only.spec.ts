import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { ONLINE_PAYMENTS_ENABLED } from '../../../core/config/payment-features';
import { Payment } from '../payment.models';
import { PaymentService } from '../payment.service';
import { PaymentHistoryPage } from './payment-history-page';

describe('COD-only payment history actions', () => {
  it('does not offer or submit new online refunds, even for a dispatcher', () => {
    const api = { refund: vi.fn(), retryRefund: vi.fn() };
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { roles: () => ['DISPATCHER'] } },
      { provide: PaymentService, useValue: api },
      { provide: ONLINE_PAYMENTS_ENABLED, useValue: false },
    ] });
    const page = TestBed.runInInjectionContext(() => new PaymentHistoryPage());
    expect(page.canRefund({ status: 'SUCCESS' } as Payment)).toBe(false);
    page.createRefund();
    page.retryRefund();
    expect(api.refund).not.toHaveBeenCalled();
    expect(api.retryRefund).not.toHaveBeenCalled();
  });
});
