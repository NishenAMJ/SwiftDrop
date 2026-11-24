import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingSummaryDto } from '../../customer/models/customer-booking.models';
import { CustomerBookingService } from '../../customer/services/customer-booking.service';
import { MerchantOrdersComponent } from './merchant-orders.component';

const order = (id: string, status: string, urgency: string, recipientName?: string, destinationCity?: string) =>
  ({ id, trackingNumber: `SWD-2026-00000${id}`, status, urgency, recipientName, destinationCity,
    description: `Order ${id}`, weightKg: 1, estimatedFee: 500, createdAt: '' }) as unknown as BookingSummaryDto;

describe('MerchantOrdersComponent', () => {
  function setup(fail = false) {
    const api = {
      getMyBookings: vi.fn(() => (fail ? throwError(() => new Error('boom')) : of([
        order('1', 'BOOKED', 'NORMAL', 'Kamala', 'Kandy'),
        order('2', 'DELIVERED', 'URGENT', 'Nimal', 'Galle'),
        order('3', 'BOOKED', 'URGENT'),
      ]))),
    };
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      imports: [MerchantOrdersComponent],
      providers: [provideRouter([]), { provide: CustomerBookingService, useValue: api }],
    });
    const fixture = TestBed.createComponent(MerchantOrdersComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, api };
  }

  it('filters orders by search, urgency and status together', () => {
    const { component } = setup();
    expect(component.filteredBookings()).toHaveLength(3);
    component.searchTerm.set('kandy');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['1']);
    component.searchTerm.set('order 3');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['3']);
    component.searchTerm.set('');
    component.setUrgencyFilter('URGENT');
    component.setStatusFilter('BOOKED');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['3']);
  });

  it('reports a failed load', () => {
    const { component } = setup(true);
    expect(component.errorMessage()).toContain('Could not fetch your booking history');
    expect(component.loading()).toBe(false);
  });

  it('reloads after cancelling and closes the dialog on errors', () => {
    const { component, api } = setup();
    component.openCancelModal('1');
    expect(component.cancelTargetId()).toBe('1');
    component.onCancelled('1');
    expect(api.getMyBookings).toHaveBeenCalledTimes(2);
    component.openCancelModal('1');
    component.onCancelError('409');
    expect(component.isCancelModalOpen()).toBe(false);
  });

  it('clears the copied marker after two seconds', () => {
    vi.useFakeTimers();
    try {
      const { component } = setup();
      component.copyTracking('SWD-2026-000001');
      vi.advanceTimersByTime(1999);
      expect(component.copiedRef()).toBe('SWD-2026-000001');
      vi.advanceTimersByTime(1);
      expect(component.copiedRef()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
