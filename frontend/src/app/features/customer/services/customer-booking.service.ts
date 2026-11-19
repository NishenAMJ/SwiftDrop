import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CancellationEligibility,
  CancellationInitiation,
  BookingCheckout,
  BookingReceiptDto,
  BookingResponseDto,
  BookingSummaryDto,
  CreateBookingRequest,
  FeeBreakdown,
  PaymentContextResponse,
  UrgencyType,
} from '../models/customer-booking.models';

export const BOOKING_API = '/api/v1/bookings';
export const PAYMENTS_API = '/api/v1/payments';

@Injectable({ providedIn: 'root' })
export class CustomerBookingService {
  private readonly http = inject(HttpClient);

  getCancellationEligibility(id: string): Observable<CancellationEligibility> {
    return this.http.get<CancellationEligibility>(BOOKING_API + '/' + encodeURIComponent(id) + '/cancellation-eligibility');
  }

  initiateCancellation(id: string, key: string): Observable<CancellationInitiation> {
    return this.http.post<CancellationInitiation>(BOOKING_API + '/' + encodeURIComponent(id) + '/cancel', {}, {
      headers: { 'Idempotency-Key': key },
    });
  }

  createBooking(request: CreateBookingRequest): Observable<BookingResponseDto> {
    return this.http.post<BookingResponseDto>(BOOKING_API, request);
  }

  getMyBookings(): Observable<BookingSummaryDto[]> {
    return this.http.get<BookingSummaryDto[]>(`${BOOKING_API}/my`);
  }

  getBookingById(id: string): Observable<BookingResponseDto> {
    return this.http.get<BookingResponseDto>(`${BOOKING_API}/${id}`);
  }

  getBookingByTrackingNumber(trackingNumber: string): Observable<BookingResponseDto> {
    return this.http.get<BookingResponseDto>(`${BOOKING_API}/track/${encodeURIComponent(trackingNumber)}`);
  }

  getReceiptByParcelId(parcelId: string): Observable<BookingReceiptDto> {
    return this.http.get<BookingReceiptDto>(`${BOOKING_API}/${parcelId}/receipt`);
  }

  getPaymentContext(bookingId: string): Observable<PaymentContextResponse> {
    return this.http.get<PaymentContextResponse>(`${BOOKING_API}/${bookingId}/payment-context`);
  }

  /**
   * Calls Payment & Settlement Service (Member 8) to initiate online checkout.
   * Sends authoritative bookingId and client idempotency key.
   */
  initiatePayment(bookingId: string, key: string = crypto.randomUUID()): Observable<BookingCheckout> {
    return this.http.post<BookingCheckout>(PAYMENTS_API, { bookingId }, { headers: { 'Idempotency-Key': key } });
  }

  getCheckout(paymentId: string): Observable<NonNullable<BookingCheckout['checkout']>> {
    return this.http.get<NonNullable<BookingCheckout['checkout']>>(PAYMENTS_API + '/' + encodeURIComponent(paymentId) + '/checkout');
  }

  confirmBookingPayment(bookingId: string, paymentId: string): Observable<BookingResponseDto> {
    return this.http.post<BookingResponseDto>(BOOKING_API + '/' + encodeURIComponent(bookingId) + '/confirm-payment', { paymentId });
  }

  /**
   * Calculates local real-time fee breakdown matching the backend BookingService formula:
   * Base Fee: 350 LKR
   * Weight Fee: 150 LKR per kg
   * Urgency Fee: 300 LKR (if URGENT)
   */
  calculateFee(weightKg: number, urgency: UrgencyType): FeeBreakdown {
    const validWeight = Math.max(0, Number(weightKg) || 0);
    const baseFee = 350;
    const weightFee = Math.round(validWeight * 150 * 100) / 100;
    const urgencySurcharge = urgency === 'URGENT' ? 300 : 0;
    const totalFee = baseFee + weightFee + urgencySurcharge;

    return {
      baseFee,
      weightFee,
      urgencySurcharge,
      totalFee,
    };
  }
}
