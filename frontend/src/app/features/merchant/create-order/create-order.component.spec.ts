import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { CustomerBookingService } from '../../customer/services/customer-booking.service';
import { CustomerProfileService } from '../../customer/services/customer-profile.service';
import { MerchantOrderService } from '../services/merchant-order.service';
import { CreateOrderComponent } from './create-order.component';

const QUOTE = { baseFee: 300, weightCharge: 50, zoneCharge: 150, urgentSurcharge: 0, total: 500 };

describe('CreateOrderComponent', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setup(query: Record<string, string> = {}, overrides: { quote?: unknown; zonesFail?: boolean } = {}) {
    const bookings = {
      getActiveZones: vi.fn(() => (overrides.zonesFail ? throwError(() => new Error('down')) : of([{ code: 'CMB', name: 'Colombo' }]))),
      getFeeQuote: vi.fn(() => (overrides.quote ? overrides.quote : of(QUOTE))),
    };
    const orders = { createOrder: vi.fn(() => of({ id: 'order-1' })) };
    TestBed.configureTestingModule({
      imports: [CreateOrderComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams: query } } },
        { provide: AuthService, useValue: { user: () => ({ fullName: 'Shop Owner', phoneNumber: '+94771234567' }), roles: () => ['MERCHANT'] } },
        { provide: CustomerProfileService, useValue: { profile: () => ({ streetAddress: '1 Shop St', city: 'Galle', postalCode: '80000' }) } },
        { provide: CustomerBookingService, useValue: bookings },
        { provide: MerchantOrderService, useValue: orders },
      ],
    });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CreateOrderComponent);
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    return { component: fixture.componentInstance, bookings, orders, navigate };
  }

  function fillValidOrder(component: CreateOrderComponent) {
    component.bookingForm.patchValue({
      merchantReference: 'ORD-1001',
      codAmount: 2500,
      deliveryAddress: { contactName: ' Kamala ', contactPhone: ' 0777654321 ', streetAddress: ' 45 Lake Rd ', city: ' Kandy ', zoneCode: 'CMB' },
    });
  }

  it('prefills the pickup from the merchant account and profile, and honours query parameters', () => {
    const { component } = setup({ weight: '3.5', urgency: 'URGENT' });
    expect(component.bookingForm.get('pickupAddress.contactName')!.value).toBe('Shop Owner');
    expect(component.bookingForm.get('pickupAddress.city')!.value).toBe('Galle');
    expect(component.bookingForm.get('weightKg')!.value).toBe(3.5);
    expect(component.selectedUrgency).toBe('URGENT');
  });

  it('ignores an invalid weight in the query and defaults to 1 kg normal delivery', () => {
    const { component } = setup({ weight: '-4', urgency: 'SOON' });
    expect(component.bookingForm.get('weightKg')!.value).toBe(1);
    expect(component.selectedUrgency).toBe('NORMAL');
  });

  it('asks the backend for a quote and re-prices when the zone or urgency changes', () => {
    const { component, bookings } = setup();
    expect(bookings.getFeeQuote).toHaveBeenCalledWith(1, 'NORMAL', null);
    expect(component.feeBreakdown()).toEqual(QUOTE);

    component.bookingForm.get('deliveryAddress.zoneCode')!.setValue('CMB');
    component.setUrgency('URGENT');
    vi.advanceTimersByTime(300);
    expect(bookings.getFeeQuote).toHaveBeenLastCalledWith(1, 'URGENT', 'CMB');
    expect(bookings.getFeeQuote).toHaveBeenCalledTimes(2);
  });

  it('does not ask for a quote for a zero weight', () => {
    const { component, bookings } = setup();
    component.bookingForm.get('weightKg')!.setValue(0);
    vi.advanceTimersByTime(300);
    expect(bookings.getFeeQuote).toHaveBeenCalledTimes(1);
    expect(component.quoteLoading()).toBe(false);
  });

  it('shows the backend message when the quote is rejected', () => {
    const rejected = throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Parcel weight exceeds maximum' } }));
    const { component } = setup({}, { quote: rejected });
    expect(component.quoteError()).toBe('Parcel weight exceeds maximum');
  });

  it('reports zones that cannot be loaded and falls back to the code for unknown zones', () => {
    const { component } = setup({}, { zonesFail: true });
    expect(component.zonesError()).toContain('could not be loaded');
    expect(component.zoneName('XYZ')).toBe('XYZ');
    expect(component.zoneName(null)).toBe('Standard zone');
  });

  it.each([
    ['weightKg', 0],
    ['weightKg', 50.01],
    ['codAmount', 0],
    ['codAmount', null],
    ['merchantReference', 'has spaces!'],
  ])('rejects an invalid %s (%s)', (field, value) => {
    const { component, orders } = setup();
    fillValidOrder(component);
    component.bookingForm.get(field)!.setValue(value);
    component.onSubmit();
    expect(orders.createOrder).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('required fields');
    expect(component.isFieldInvalid(field)).toBe(true);
  });

  it('requires a delivery zone', () => {
    const { component, orders } = setup();
    fillValidOrder(component);
    component.bookingForm.get('deliveryAddress.zoneCode')!.setValue('');
    component.onSubmit();
    expect(orders.createOrder).not.toHaveBeenCalled();
  });

  it('submits a trimmed cash-on-delivery order and opens its receipt', () => {
    const { component, orders, navigate } = setup();
    fillValidOrder(component);
    component.bookingForm.get('urgentNote')!.setValue('ignored for normal delivery');
    component.onSubmit();
    expect(orders.createOrder).toHaveBeenCalledWith(expect.objectContaining({
      merchantReference: 'ORD-1001',
      paymentMethod: 'COD',
      codAmount: 2500,
      urgentNote: undefined,
      deliveryAddress: expect.objectContaining({ contactName: 'Kamala', city: 'Kandy', zoneCode: 'CMB', postalCode: undefined }),
    }));
    expect(navigate).toHaveBeenCalledWith(['/merchant/receipt', 'order-1'], { state: { newBooking: true } });
  });

  it.each([
    [new HttpErrorResponse({ status: 409, error: {} }), 'An order with this reference already exists.'],
    [new HttpErrorResponse({ status: 400, error: { fieldErrors: { codAmount: 'COD amount is too high' } } }), 'COD amount is too high'],
    [new HttpErrorResponse({ status: 0 }), 'You appear to be offline'],
    [new HttpErrorResponse({ status: 500, error: { message: 'NullPointerException at OrderService' } }), 'could not be created right now'],
  ])('maps a failed order to a safe message (%#)', (error, text) => {
    const { component, orders } = setup();
    orders.createOrder.mockReturnValueOnce(throwError(() => error) as never);
    fillValidOrder(component);
    component.onSubmit();
    expect(component.errorMessage()).toContain(text);
    expect(component.errorMessage()).not.toContain('NullPointerException');
    expect(component.submitting()).toBe(false);
  });
});
