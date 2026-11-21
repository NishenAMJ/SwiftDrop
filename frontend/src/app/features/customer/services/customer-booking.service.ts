import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  BookingCheckout,
  BookingReceiptDto,
  BookingResponseDto,
  BookingSummaryDto,
  CancellationEligibility,
  CancellationInitiation,
  CreateBookingRequest,
  DeliveryZone,
  FeeBreakdown,
  PaymentContextResponse,
  PricingQuoteResponse,
  UrgencyType,
} from '../models/customer-booking.models';

export const BOOKING_API = '/api/v1/bookings';
export const PAYMENTS_API = '/api/v1/payments';
export const PRICING_API = '/api/pricing/calculate';
export const ZONES_API = '/api/zones';

@Injectable({ providedIn: 'root' })
export class CustomerBookingService {
  private readonly http = inject(HttpClient);

  // ---------------------------------------------------------------- pricing and zones

  /**
   * Fee estimate from the backend pricing module (pricing rules and zones managed by dispatchers).
   * The same calculation is used when the booking is saved, so the estimate matches the charge.
   * Without a zone the pricing rule's default zone charge is used.
   */
  getFeeQuote(weightKg: number, urgency: UrgencyType, zoneCode?: string | null): Observable<FeeBreakdown> {
    return this.http
      .post<PricingQuoteResponse>(PRICING_API, {
        weightKg,
        zoneCode: zoneCode || null,
        isUrgent: urgency === 'URGENT',
      })
      .pipe(
        map((quote) => ({
          baseFee: quote.baseFee,
          weightFee: quote.weightCharge,
          ratePerKg: quote.ratePerKg,
          zoneCharge: quote.zoneCharge,
          zoneCode: quote.zoneCode,
          urgencySurcharge: quote.urgentSurcharge,
          urgentSurchargeApplied: quote.urgentSurchargeApplied,
          totalFee: quote.totalDeliveryFee,
        })),
      );
  }

  /** Active delivery zones for the zone dropdowns. */
  getActiveZones(): Observable<DeliveryZone[]> {
    return this.http.get<DeliveryZone[]>(ZONES_API, { params: { status: 'ACTIVE' } });
  }

  // ---------------------------------------------------------------- bookings

  createBooking(request: CreateBookingRequest): Observable<BookingResponseDto> {
    return this.http.post<BookingResponseDto>(BOOKING_API, request);
  }

  getMyBookings(): Observable<BookingSummaryDto[]> {
    return this.http.get<BookingSummaryDto[]>(`${BOOKING_API}/my`);
  }

  getBookingById(id: string): Observable<BookingResponseDto> {
    return this.http.get<BookingResponseDto>(`${BOOKING_API}/${encodeURIComponent(id)}`);
  }

  /** Only finds the signed-in customer's own bookings. Anyone else uses public tracking. */
  getBookingByTrackingNumber(trackingNumber: string): Observable<BookingResponseDto> {
    return this.http.get<BookingResponseDto>(`${BOOKING_API}/track/${encodeURIComponent(trackingNumber)}`);
  }

  getReceiptByParcelId(parcelId: string): Observable<BookingReceiptDto> {
    return this.http.get<BookingReceiptDto>(`${BOOKING_API}/${encodeURIComponent(parcelId)}/receipt`);
  }

  getPaymentContext(bookingId: string): Observable<PaymentContextResponse> {
    return this.http.get<PaymentContextResponse>(`${BOOKING_API}/${encodeURIComponent(bookingId)}/payment-context`);
  }

  /** Leave an unpaid online checkout. Nothing is charged and it never appears in history. */
  discardDraft(bookingId: string): Observable<void> {
    return this.http.post<void>(`${BOOKING_API}/${encodeURIComponent(bookingId)}/discard`, {});
  }

  // ---------------------------------------------------------------- payment (Payment & Settlement service)

  /** Starts online checkout. The same idempotency key on retry never creates a second charge. */
  initiatePayment(bookingId: string, key: string = crypto.randomUUID()): Observable<BookingCheckout> {
    return this.http.post<BookingCheckout>(PAYMENTS_API, { bookingId }, { headers: { 'Idempotency-Key': key } });
  }

  getCheckout(paymentId: string): Observable<NonNullable<BookingCheckout['checkout']>> {
    return this.http.get<NonNullable<BookingCheckout['checkout']>>(
      `${PAYMENTS_API}/${encodeURIComponent(paymentId)}/checkout`,
    );
  }

  /** Asks this service to verify the payment with Payment & Settlement and confirm the booking. */
  confirmBookingPayment(bookingId: string, paymentId: string): Observable<BookingResponseDto> {
    return this.http.post<BookingResponseDto>(`${BOOKING_API}/${encodeURIComponent(bookingId)}/confirm-payment`, {
      paymentId,
    });
  }

  // ---------------------------------------------------------------- cancellation

  getCancellationEligibility(id: string): Observable<CancellationEligibility> {
    return this.http.get<CancellationEligibility>(`${BOOKING_API}/${encodeURIComponent(id)}/cancellation-eligibility`);
  }

  initiateCancellation(id: string, key: string): Observable<CancellationInitiation> {
    return this.http.post<CancellationInitiation>(`${BOOKING_API}/${encodeURIComponent(id)}/cancel`, {}, {
      headers: { 'Idempotency-Key': key },
    });
  }
}
