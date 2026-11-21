import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CancelBookingComponent } from './cancel-booking.component';

describe('CancelBookingComponent', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [CancelBookingComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function setup() {
    const fixture = TestBed.createComponent(CancelBookingComponent);
    fixture.componentRef.setInput('bookingId', 'booking-123');
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  const bookedCod = {
    id: 'booking-123', trackingNumber: 'SWD-2026-000123', paymentMethod: 'COD', estimatedFee: 550, status: 'BOOKED', weightKg: 2,
  };

  it('shows why a scheduled booking cannot be cancelled and offers no confirmation', () => {
    const { fixture, http } = setup();
    expect(fixture.nativeElement.textContent).toContain('Checking whether this booking can be cancelled');
    const request = http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility');
    expect(request.request.method).toBe('GET');
    request.flush({
      eligible: false,
      reason: 'A courier has already been scheduled for this parcel, so it can no longer be cancelled.',
      cancellationFee: 0,
      booking: { ...bookedCod, status: 'ASSIGNED' },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('courier has already been scheduled');
    fixture.componentInstance.advance();
    fixture.detectChanges();
    const submit = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    fixture.componentInstance.advance();
    http.expectNone((r) => r.method === 'POST');
  });

  it('cancels a booked COD parcel at no charge after explicit confirmation', () => {
    const { fixture, http } = setup();
    http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility').flush({
      eligible: true, reason: null, cancellationFee: 0, booking: bookedCod,
    });
    fixture.detectChanges();
    fixture.componentInstance.advance();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cancel booking');
    http.expectNone((r) => r.method === 'POST');

    fixture.componentInstance.advance();
    const request = http.expectOne('/api/v1/bookings/booking-123/cancel');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({ trackingNumber: 'SWD-2026-000123', bookingStatus: 'CANCELLED', cancellationStatus: 'COMPLETED', completed: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('is cancelled. Nothing was charged.');

    // Only the top-right close button remains on the final card.
    expect(fixture.nativeElement.querySelector('.sd-dialog__actions')).toBeNull();
    let cancelled = '';
    fixture.componentInstance.cancelled.subscribe((id) => (cancelled = id));
    (fixture.nativeElement.querySelector('button[aria-label="Close dialog"]') as HTMLButtonElement).click();
    expect(cancelled).toBe('booking-123');
  });

  it('explains the refund for a paid online booking', () => {
    const { fixture, http } = setup();
    http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility').flush({
      eligible: true, reason: null, cancellationFee: 0, originalPaidAmount: 550, refundableAmount: 550,
      booking: { ...bookedCod, paymentMethod: 'ONLINE' },
    });
    fixture.detectChanges();
    fixture.componentInstance.advance();
    fixture.componentInstance.advance();
    http.expectOne('/api/v1/bookings/booking-123/cancel').flush({
      trackingNumber: 'SWD-2026-000123', cancellationStatus: 'REFUND_PENDING', refundAmount: 550, completed: true,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('refund of LKR 550.00 has been requested');
  });

  it('closes the dialog using the top-right close button', () => {
    const { fixture, http } = setup();
    http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility').flush({ eligible: false, reason: 'Unavailable', booking: null });
    fixture.detectChanges();
    let closed = false;
    fixture.componentInstance.closed.subscribe(() => (closed = true));
    (fixture.nativeElement.querySelector('button[aria-label="Close dialog"]') as HTMLButtonElement).click();
    expect(closed).toBe(true);
  });

  it('handles a network error without exposing the server response', () => {
    const { fixture, http } = setup();
    http
      .expectOne('/api/v1/bookings/booking-123/cancellation-eligibility')
      .flush({ message: 'private internal detail' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Please retry');
    expect(fixture.nativeElement.textContent).not.toContain('private internal detail');
  });
});
