import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { BookParcelComponent } from './book-parcel.component';
import { ONLINE_PAYMENTS_ENABLED } from '../../../core/config/payment-features';
import { AuthService } from '../../../core/auth/auth.service';
import { CustomerProfileService } from '../services/customer-profile.service';

describe('Booking and payment flow', () => {
  const navigate = vi.fn();
  const zones = [{ id: 1, name: 'Kandy District', code: 'KANDY', status: 'ACTIVE' }];

  beforeEach(() => {
    localStorage.clear();
    navigate.mockClear();
    TestBed.configureTestingModule({
      imports: [BookParcelComponent],
      providers: [
        { provide: ONLINE_PAYMENTS_ENABLED, useValue: true },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams: {} } } },
        { provide: AuthService, useValue: { user: signal({ id: 'customer-test', fullName: 'Sender', phoneNumber: '0771234567' }) } },
        { provide: CustomerProfileService, useValue: { profile: signal({ streetAddress: 'Sender street', city: 'Colombo' }) } },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  function setup(method: 'ONLINE' | 'COD') {
    const fixture = TestBed.createComponent(BookParcelComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/zones').flush(zones);
    fixture.componentInstance.bookingForm.patchValue({
      weightKg: 1,
      paymentMethod: method,
      deliveryAddress: {
        contactName: 'Recipient',
        contactPhone: '0777654321',
        streetAddress: 'Recipient street',
        city: 'Kandy',
        zoneCode: 'KANDY',
      },
    });
    return { component: fixture.componentInstance, http };
  }

  const draft = {
    id: 'draft-123',
    trackingNumber: 'SWD-2026-000123',
    paymentMethod: 'ONLINE',
    status: 'CHECKOUT_DRAFT',
    estimatedFee: 650,
  };

  it('defaults to COD and hides online checkout when online payments are disabled', () => {
    TestBed.overrideProvider(ONLINE_PAYMENTS_ENABLED, { useValue: false });
    const fixture = TestBed.createComponent(BookParcelComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === '/api/zones').flush(zones);
    const component = fixture.componentInstance;
    expect(component.selectedPaymentMethod).toBe('COD');
    expect(fixture.nativeElement.textContent).not.toContain('Online Card Payment');
    component.setPaymentMethod('ONLINE');
    expect(component.selectedPaymentMethod).toBe('COD');
    component.bookingForm.patchValue({ paymentMethod: 'ONLINE' });
    component.onSubmit();
    expect(component.errorMessage()).toContain('unavailable');
    http.expectNone('/api/v1/bookings');
    http.expectNone('/api/v1/payments');
  });

  it('preserves a saved online checkout without reopening it while disabled', () => {
    TestBed.overrideProvider(ONLINE_PAYMENTS_ENABLED, { useValue: false });
    const saved = JSON.stringify({ id: 'draft-123', key: 'saved-key', paymentId: 'payment-123' });
    localStorage.setItem('swiftdrop:booking-checkout:customer-test', saved);
    const { component, http } = setup('COD');
    component.startPayment();
    component.payNow();
    expect(component.errorMessage()).toContain('previous online checkout');
    expect(localStorage.getItem('swiftdrop:booking-checkout:customer-test')).toBe(saved);
    http.expectNone('/api/v1/bookings/draft-123');
    http.expectNone('/api/v1/payments');
  });

  it('gets the fee from the pricing module for the chosen zone', () => {
    vi.useFakeTimers();
    const { component, http } = setup('COD');
    vi.advanceTimersByTime(400);
    const quote = http.expectOne('/api/pricing/calculate');
    expect(quote.request.body).toEqual({ weightKg: 1, zoneCode: 'KANDY', isUrgent: false });
    quote.flush({
      totalDeliveryFee: 650, baseFee: 300, weightCharge: 50, weightKg: 1, ratePerKg: 50, zoneCharge: 300,
      zoneCode: 'KANDY', urgentSurcharge: 0, urgentSurchargeApplied: false, currency: 'LKR',
      appliedRuleCode: 'STANDARD_DEFAULT', breakdownDescription: '',
    });
    expect(component.feeBreakdown().totalFee).toBe(650);
    expect(component.feeBreakdown().zoneCharge).toBe(300);
  });

  it('requires a delivery zone before booking', () => {
    const { component, http } = setup('COD');
    component.bookingForm.get('deliveryAddress.zoneCode')?.setValue('');
    component.onSubmit();
    http.expectNone('/api/v1/bookings');
    expect(component.errorMessage()).toContain('required fields');
  });

  it('shows the COD receipt immediately without an online payment', () => {
    const { component, http } = setup('COD');
    component.onSubmit();
    http.expectOne('/api/v1/bookings').flush({ ...draft, paymentMethod: 'COD', status: 'BOOKED', receiptNumber: 'RCP-2026-000123' });
    expect(navigate).toHaveBeenCalledWith(['/customer/receipt', 'draft-123'], { state: { newBooking: true } });
    http.expectNone('/api/v1/payments');
  });

  it('online booking goes straight to payment and is not confirmed before verification', () => {
    const { component, http } = setup('ONLINE');
    component.onSubmit();
    http.expectOne('/api/v1/bookings').flush(draft);
    expect(component.paymentOpen()).toBe(true);

    const payment = http.expectOne('/api/v1/payments');
    expect(payment.request.headers.get('Idempotency-Key')).toBeTruthy();
    payment.flush({ id: 'payment-123', status: 'PENDING' });

    // While checking, the dialog shows progress only, no buttons.
    expect(component.paymentStep()).toBe('verifying');
    expect(component.paymentHasActions).toBe(false);
    http.expectOne('/api/v1/bookings/draft-123/confirm-payment').flush({}, { status: 409, statusText: 'Pending' });

    // Last verification attempt: still not paid, so no receipt and a clear error with choices.
    component.verifyPayment(5);
    http.expectOne('/api/v1/bookings/draft-123/confirm-payment').flush({}, { status: 409, statusText: 'Pending' });
    expect(navigate).not.toHaveBeenCalled();
    expect(component.paymentStep()).toBe('not-confirmed');
    expect(component.paymentTitle).toBe('Payment not completed');
    expect(component.paymentActionLabel).toBe('Check again');
    expect(component.paymentHasActions).toBe(true);
  });

  it('shows the receipt only after the backend confirms the payment', () => {
    const { component, http } = setup('ONLINE');
    component.onSubmit();
    http.expectOne('/api/v1/bookings').flush(draft);
    http.expectOne('/api/v1/payments').flush({ id: 'payment-123', status: 'PENDING' });

    http.expectOne('/api/v1/bookings/draft-123/confirm-payment')
      .flush({ ...draft, status: 'BOOKED', receiptNumber: 'RCP-2026-000123' });
    expect(navigate).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/customer/receipt', 'draft-123'], { state: { newBooking: true } });
    expect(component.paymentOpen()).toBe(false);
  });

  it('retries a failed payment start with the same idempotency key', () => {
    const { component, http } = setup('ONLINE');
    component.onSubmit();
    http.expectOne('/api/v1/bookings').flush(draft);
    const first = http.expectOne('/api/v1/payments');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(navigate).not.toHaveBeenCalled();

    component.paymentAction();
    const retry = http.expectOne('/api/v1/payments');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ id: 'payment-123', status: 'PENDING' });
    http.expectOne('/api/v1/bookings/draft-123/confirm-payment')
      .flush({ ...draft, status: 'BOOKED', receiptNumber: 'RCP-2026-000123' });
  });

  it('checks the payment again when Back from PayHere restores the cached page', () => {
    const { component, http } = setup('ONLINE');
    component.onSubmit();
    http.expectOne('/api/v1/bookings').flush(draft);
    http.expectOne('/api/v1/payments').flush({}, { status: 503, statusText: 'Unavailable' });
    component.checkout.set({ id: 'payment-123', status: 'PENDING' });

    component.onPageShow({ persisted: true } as PageTransitionEvent);
    http.expectOne('/api/v1/bookings/draft-123/confirm-payment')
      .flush({ ...draft, status: 'BOOKED', receiptNumber: 'RCP-2026-000123' });
    expect(navigate).toHaveBeenCalledWith(['/customer/receipt', 'draft-123'], { state: { newBooking: true } });
  });

  it('verifies automatically when the customer returns from the payment page', () => {
    localStorage.setItem(
      'swiftdrop:booking-checkout:customer-test',
      JSON.stringify({ id: 'draft-123', key: 'saved-key', paymentId: 'payment-123' }),
    );
    const fixture = TestBed.createComponent(BookParcelComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/zones').flush(zones);
    http.expectOne('/api/v1/bookings/draft-123').flush(draft);
    http.expectOne('/api/v1/payments/payment-123/checkout').flush({ provider: 'PAYHERE', checkoutUrl: 'https://sandbox.payhere.lk/pay/checkout', method: 'POST', fields: {} });
    http.expectOne('/api/v1/bookings/draft-123/confirm-payment')
      .flush({ ...draft, status: 'BOOKED', receiptNumber: 'RCP-2026-000123' });
    expect(navigate).toHaveBeenCalledWith(['/customer/receipt', 'draft-123'], { state: { newBooking: true } });
    expect(localStorage.getItem('swiftdrop:booking-checkout:customer-test')).toBeNull();
  });

  it('lets the customer discard an unpaid checkout', () => {
    const { component, http } = setup('ONLINE');
    component.onSubmit();
    http.expectOne('/api/v1/bookings').flush(draft);
    http.expectOne('/api/v1/payments').flush({}, { status: 503, statusText: 'Unavailable' });

    component.discardCheckout();
    http.expectOne('/api/v1/bookings/draft-123/discard').flush(null);
    expect(component.draft()).toBeNull();
    expect(component.bookingForm.enabled).toBe(true);
  });
});
