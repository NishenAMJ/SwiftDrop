import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { BookingSummaryDto } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { CustomerProfileService } from '../services/customer-profile.service';
import { CustomerDashboardComponent } from './customer-dashboard.component';

const summary = (id: string, status: string, urgency = 'NORMAL') =>
  ({ id, trackingNumber: `SWD-2026-00000${id}`, status, urgency, description: 'x', weightKg: 1, estimatedFee: 500, createdAt: '' }) as unknown as BookingSummaryDto;

describe('CustomerDashboardComponent', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setup(fail = false) {
    const api = {
      getMyBookings: vi.fn(() => (fail ? throwError(() => new Error('x')) : of([
        summary('1', 'BOOKED', 'URGENT'), summary('2', 'OUT_FOR_DELIVERY'), summary('3', 'DELIVERED'), summary('4', 'CANCELLED'),
      ]))),
      getBookingByTrackingNumber: vi.fn(() => of({ id: 'p1' })),
      getFeeQuote: vi.fn(() => of({ baseFee: 300, weightCharge: 100, zoneCharge: 150, urgentSurcharge: 0, total: 550 })),
    };
    TestBed.configureTestingModule({
      imports: [CustomerDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: CustomerBookingService, useValue: api },
        { provide: AuthService, useValue: { user: signal({ fullName: 'Nimal' }) } },
        { provide: CustomerProfileService, useValue: { profile: signal({}), customerName: signal('Nimal') } },
      ],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CustomerDashboardComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, api, navigate };
  }

  it('summarises the customer bookings', () => {
    const { component } = setup();
    expect(component.totalCount).toBe(4);
    expect(component.activeCount).toBe(2);
    expect(component.urgentCount).toBe(1);
    expect(component.deliveredCount).toBe(1);
  });

  it('shows a retry message when bookings fail to load', () => {
    const { component } = setup(true);
    expect(component.errorMessage()).toContain('Could not fetch recent bookings');
  });

  it('only finds the customers own bookings and points to public tracking otherwise', () => {
    const { component, api } = setup();
    api.getBookingByTrackingNumber.mockReturnValueOnce(throwError(() => ({ status: 404 })) as never);
    component.trackQuery.set('SWD-2026-123456');
    component.onTrackSubmit();
    expect(component.trackError()).toContain('use public tracking');
    component.trackQuery.set('SWD-2026-000001');
    component.onTrackSubmit();
    expect(component.trackResult()).toEqual({ id: 'p1' });
  });

  it('debounces estimates and shows a backend validation message', () => {
    const { component, api } = setup();
    component.estimateWeight.set(2);
    component.updateEstimate();
    component.setEstimateUrgency('URGENT');
    vi.advanceTimersByTime(300);
    expect(api.getFeeQuote).toHaveBeenCalledTimes(1);
    expect(api.getFeeQuote).toHaveBeenCalledWith(2, 'URGENT');

    api.getFeeQuote.mockReturnValueOnce(throwError(() =>
      new HttpErrorResponse({ status: 400, error: { code: 'WEIGHT_EXCEEDS_MAXIMUM', message: 'Parcel too heavy' } })) as never);
    component.updateEstimate();
    vi.advanceTimersByTime(300);
    expect(component.estimateError()).toBe('Parcel too heavy');

    api.getFeeQuote.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })) as never);
    component.updateEstimate();
    vi.advanceTimersByTime(300);
    expect(component.estimateError()).toBe('Estimate not available right now');
    expect(component.estimateLoading()).toBe(false);
  });

  it('does not request an estimate without a weight', () => {
    const { component, api } = setup();
    component.updateEstimate();
    vi.advanceTimersByTime(300);
    expect(api.getFeeQuote).not.toHaveBeenCalled();
  });

  it('starts a booking with the estimate', () => {
    const { component, navigate } = setup();
    component.estimateWeight.set(1.5);
    component.bookWithEstimate();
    expect(navigate).toHaveBeenCalledWith(['/customer/book'], { queryParams: { weight: 1.5, urgency: 'NORMAL' } });
  });
});
