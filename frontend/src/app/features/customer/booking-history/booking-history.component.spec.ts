import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingSummaryDto } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { BookingHistoryComponent } from './booking-history.component';

function booking(overrides: Partial<BookingSummaryDto>): BookingSummaryDto {
  return {
    id: 'b1',
    trackingNumber: 'SWD-2026-000001',
    description: 'Documents',
    weightKg: 1,
    urgency: 'NORMAL',
    status: 'BOOKED',
    estimatedFee: 550,
    createdAt: '2026-11-20T10:00:00Z',
    recipientName: 'Kamala Silva',
    destinationCity: 'Kandy',
    paymentMethod: 'COD',
    cancellable: true,
    ...overrides,
  } as BookingSummaryDto;
}

describe('BookingHistoryComponent', () => {
  function setup(result: 'ok' | 'empty' | 'error' = 'ok') {
    const bookings = [
      booking({}),
      booking({ id: 'b2', trackingNumber: 'SWD-2026-000002', urgency: 'URGENT', status: 'DELIVERED',
        recipientName: 'Nimal', destinationCity: 'Galle', description: 'Laptop', cancellable: false }),
      booking({ id: 'b3', trackingNumber: 'SWD-2026-000003', status: 'IN_TRANSIT', recipientName: undefined,
        destinationCity: undefined, description: 'Shoes', cancellable: false }),
    ];
    const api = {
      getMyBookings: vi.fn(() =>
        result === 'error' ? throwError(() => new Error('connect ECONNREFUSED 10.0.0.4:8082')) : of(result === 'empty' ? [] : bookings)),
    };
    TestBed.configureTestingModule({
      imports: [BookingHistoryComponent],
      providers: [provideRouter([]), { provide: CustomerBookingService, useValue: api }],
    });
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const fixture = TestBed.createComponent(BookingHistoryComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, api, text: () => fixture.nativeElement.textContent as string };
  }

  it('loads and lists the customer bookings', () => {
    const { component, text } = setup();
    expect(component.loading()).toBe(false);
    expect(component.filteredBookings()).toHaveLength(3);
    expect(text()).toContain('SWD-2026-000002');
    expect(text()).toContain('Showing 3 of 3 bookings');
  });

  it('shows a safe error without the server address', () => {
    const { text, component } = setup('error');
    expect(component.errorMessage()).toContain('Could not fetch your booking history');
    expect(text()).not.toContain('10.0.0.4');
  });

  it('shows the empty state when there are no bookings', () => {
    const { text } = setup('empty');
    expect(text()).toContain('No matching bookings found');
  });

  it('searches tracking number, recipient, city and description case-insensitively', () => {
    const { component } = setup();
    component.searchTerm.set('  swd-2026-000002 ');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['b2']);
    component.searchTerm.set('kamala');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['b1']);
    component.searchTerm.set('GALLE');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['b2']);
    component.searchTerm.set('shoes');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['b3']);
    component.searchTerm.set('nothing-matches');
    expect(component.filteredBookings()).toEqual([]);
  });

  it('combines urgency and status filters', () => {
    const { component } = setup();
    component.setUrgencyFilter('URGENT');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['b2']);
    component.setUrgencyFilter('ALL');
    component.setStatusFilter('IN_TRANSIT');
    expect(component.filteredBookings().map((b) => b.id)).toEqual(['b3']);
    component.setUrgencyFilter('URGENT');
    expect(component.filteredBookings()).toEqual([]);
  });

  it('reloads after a cancellation and closes the dialog on errors', () => {
    const { component, api } = setup();
    component.openCancelModal('b1');
    expect(component.isCancelModalOpen()).toBe(true);
    expect(component.cancelTargetId()).toBe('b1');
    component.onCancelled('b1');
    expect(component.isCancelModalOpen()).toBe(false);
    expect(api.getMyBookings).toHaveBeenCalledTimes(2);

    component.openCancelModal('b1');
    component.onCancelError(new Error('409'));
    expect(component.isCancelModalOpen()).toBe(false);
  });

  it('remembers the copied tracking number briefly', () => {
    vi.useFakeTimers();
    try {
      const { component } = setup();
      component.copyTracking('SWD-2026-000001');
      expect(component.copiedRef()).toBe('SWD-2026-000001');
      vi.advanceTimersByTime(2000);
      expect(component.copiedRef()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
