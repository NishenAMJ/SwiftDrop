import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { CustomerBookingService } from '../services/customer-booking.service';
import { BookingReceiptComponent } from './booking-receipt.component';

describe('BookingReceiptComponent', () => {
  function setup(id: string | null, fails = false, url = '/customer/receipt/b1') {
    const api = {
      getReceiptByParcelId: vi.fn(() =>
        fails ? throwError(() => ({ status: 409, error: { detail: 'PAYMENT_REQUIRED internal' } }))
          : of({ receiptNumber: 'RCP-2026-000001', trackingNumber: 'SWD-2026-000001' } as never)),
    };
    TestBed.configureTestingModule({
      imports: [BookingReceiptComponent],
      providers: [
        provideRouter([]),
        { provide: CustomerBookingService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      ],
    });
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue(url);
    const fixture = TestBed.createComponent(BookingReceiptComponent);
    fixture.componentInstance.ngOnInit();
    return { component: fixture.componentInstance, api };
  }

  it('loads the receipt for the booking in the address', () => {
    const { component, api } = setup('b1');
    expect(api.getReceiptByParcelId).toHaveBeenCalledWith('b1');
    expect(component.receipt()?.receiptNumber).toBe('RCP-2026-000001');
    expect(component.loading()).toBe(false);
  });

  it('explains that unpaid bookings have no receipt, without server details', () => {
    const { component } = setup('b1', true);
    expect(component.errorMessage()).toContain('Complete online payment');
    expect(component.errorMessage()).not.toContain('internal');
  });

  it('rejects a missing booking id without calling the API', () => {
    const { component, api } = setup(null);
    expect(component.errorMessage()).toBe('Invalid booking ID specified.');
    expect(api.getReceiptByParcelId).not.toHaveBeenCalled();
  });

  it('links merchants back to their own pages', () => {
    const { component } = setup('b1', false, '/merchant/receipt/b1');
    expect(component.isMerchantView).toBe(true);
    expect(component.bookingsLink).toBe('/merchant/bookings');
    expect(component.newBookingLink).toBe('/merchant/create-order');
  });
});
