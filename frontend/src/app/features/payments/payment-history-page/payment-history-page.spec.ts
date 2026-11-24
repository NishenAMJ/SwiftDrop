import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { PaymentService } from '../payment.service';
import { Payment } from '../payment.models';
import { PaymentHistoryPage } from './payment-history-page';
import { ONLINE_PAYMENTS_ENABLED } from '../../../core/config/payment-features';

describe('Cancellation refund visibility', () => {
  const page = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  function setup(roles: string[], fails = false) {
    const api = {
      history: vi.fn(() => of(page)),
      cancellations: vi.fn(() => fails ? throwError(() => new Error('private backend detail')) : of(page)),
    };
    TestBed.configureTestingModule({
      imports: [PaymentHistoryPage],
      providers: [provideRouter([]), { provide: ONLINE_PAYMENTS_ENABLED, useValue: true }, { provide: AuthService, useValue: { roles: () => roles } },
        { provide: PaymentService, useValue: api }],
    });
    const fixture = TestBed.createComponent(PaymentHistoryPage);
    fixture.detectChanges();
    return { fixture, api };
  }
  it('loads dispatcher cancellations and provides an empty state', () => {
    const { fixture, api } = setup(['DISPATCHER']);
    expect(api.cancellations).toHaveBeenCalledWith(0);
    expect(fixture.nativeElement.textContent).toContain('No cancellation refunds have been received.');
  });
  it('does not request dispatcher records for customers', () => {
    const { fixture, api } = setup(['CUSTOMER']);
    expect(api.cancellations).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#cancellation-heading')).toBeNull();
  });
  it('shows a safe retry message on failure', () => {
    const { fixture } = setup(['DISPATCHER'], true);
    expect(fixture.nativeElement.textContent).toContain('Cancellation refunds could not be loaded.');
    expect(fixture.nativeElement.textContent).not.toContain('private backend detail');
  });
  it('renders a selected cancellation payment even when it is outside the current history page', () => {
    const { fixture } = setup(['DISPATCHER']);
    const payment = { id: 'off-page-payment', bookingId: 'booking', amount: 1000, currency: 'LKR',
      status: 'SUCCESS', refundedAmount: 0, provider: 'PAYHERE', providerReference: '123',
      createdAt: '2026-11-22T00:00:00Z', updatedAt: '2026-11-22T00:00:00Z' } as Payment;
    fixture.componentInstance.selectedPayment.set(payment);
    fixture.detectChanges();
    expect(fixture.componentInstance.visiblePayments()).toEqual([payment]);
    expect(fixture.nativeElement.textContent).toContain('booking');
  });
});
