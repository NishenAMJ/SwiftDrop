import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { BookingSummaryDto } from '../../customer/models/customer-booking.models';
import { CustomerBookingService } from '../../customer/services/customer-booking.service';
import { MerchantDashboardComponent } from './merchant-dashboard.component';

const summary = (id: string, status: string, urgency = 'NORMAL') =>
  ({ id, trackingNumber: `SWD-2026-00000${id}`, status, urgency, description: 'x', weightKg: 1, estimatedFee: 500, createdAt: '' }) as unknown as BookingSummaryDto;

describe('MerchantDashboardComponent', () => {
  function setup(fail = false) {
    const bookings = [summary('1', 'BOOKED', 'URGENT'), summary('2', 'IN_TRANSIT'), summary('3', 'DELIVERED'),
      summary('4', 'CANCELLED'), summary('5', 'OUT_FOR_DELIVERY', 'URGENT'), summary('6', 'DELIVERED')];
    const api = {
      getMyBookings: vi.fn(() => (fail ? throwError(() => new Error('db timeout at 10.0.0.3')) : of(bookings))),
      getBookingByTrackingNumber: vi.fn(() => of({ id: 'p1', trackingNumber: 'SWD-2026-000001' })),
      getFeeQuote: vi.fn(() => of({ baseFee: 300, weightFee: 100, urgencySurcharge: 0, totalFee: 400 })),
    };
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      imports: [MerchantDashboardComponent],
      providers: [provideRouter([]), { provide: CustomerBookingService, useValue: api },
        { provide: AuthService, useValue: { user: () => ({ fullName: 'Lanka Books' }) } }],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(MerchantDashboardComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, api, navigate, fixture };
  }

  it('computes the KPI tiles from the merchant bookings', () => {
    const { component } = setup();
    expect(component.totalCount).toBe(6);
    expect(component.activeCount).toBe(3);
    expect(component.urgentCount).toBe(2);
    expect(component.deliveredCount).toBe(2);
    expect(component.recentBookings).toHaveLength(5);
    expect(component.merchantName()).toBe('Lanka Books');
  });

  it('shows a safe error when bookings cannot be loaded', () => {
    const { component, fixture } = setup(true);
    expect(component.errorMessage()).toContain('Could not fetch recent bookings');
    expect(fixture.nativeElement.textContent).not.toContain('10.0.0.3');
  });

  it('tracks a parcel by a trimmed reference and reports unknown references', () => {
    const { component, api } = setup();
    component.trackQuery.set('   ');
    component.onTrackSubmit();
    expect(api.getBookingByTrackingNumber).not.toHaveBeenCalled();

    component.trackQuery.set(' SWD-2026-000001 ');
    component.onTrackSubmit();
    expect(api.getBookingByTrackingNumber).toHaveBeenCalledWith('SWD-2026-000001');
    expect(component.trackResult()?.id).toBe('p1');

    api.getBookingByTrackingNumber.mockReturnValueOnce(throwError(() => ({ status: 404 })) as never);
    component.trackQuery.set('SWD-2026-999999');
    component.onTrackSubmit();
    expect(component.trackError()).toContain('SWD-2026-999999');
    expect(component.trackingLoading()).toBe(false);
  });

  it('only prices positive weights and uses the backend quote', () => {
    const { component, api } = setup();
    expect(api.getFeeQuote).not.toHaveBeenCalled();
    component.estimateWeight.set(2);
    component.setEstimateUrgency('URGENT');
    expect(api.getFeeQuote).toHaveBeenCalledWith(2, 'URGENT');
    expect(component.feeBreakdown().totalFee).toBe(400);

    api.getFeeQuote.mockReturnValueOnce(throwError(() => ({ status: 400 })) as never);
    component.updateEstimate();
    expect(component.feeBreakdown().totalFee).toBe(0);
  });

  it('opens the order form with the estimated weight and urgency', () => {
    const { component, navigate } = setup();
    component.estimateWeight.set(3);
    component.estimateUrgency.set('URGENT');
    component.bookWithEstimate();
    expect(navigate).toHaveBeenCalledWith(['/merchant/create-order'], { queryParams: { weight: 3, urgency: 'URGENT' } });
  });
});
