import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export const MERCHANT_ORDERS_API = '/api/merchant/orders';

/**
 * Merchant orders (parcel-pricing-service). A merchant order is a normal booking: it gets an SWD
 * tracking number, appears in My Bookings and has a receipt. Fees come from the backend pricing
 * module (use CustomerBookingService.getFeeQuote), never from a formula in the browser.
 */
@Injectable({ providedIn: 'root' })
export class MerchantOrderService {
  private readonly http = inject(HttpClient);

  createOrder(request: CreateMerchantOrderRequest): Observable<MerchantOrder> {
    return this.http.post<MerchantOrder>(MERCHANT_ORDERS_API, request);
  }

  /** The merchant's orders, newest first. The total count comes from the X-Total-Count header. */
  getOrders(page = 0, size = 20): Observable<{ orders: MerchantOrder[]; total: number }> {
    return this.http
      .get<MerchantOrder[]>(MERCHANT_ORDERS_API, { params: { page, size }, observe: 'response' })
      .pipe(
        map((res: HttpResponse<MerchantOrder[]>) => ({
          orders: res.body ?? [],
          total: Number(res.headers.get('X-Total-Count') ?? res.body?.length ?? 0),
        })),
      );
  }

  getOrder(id: string): Observable<MerchantOrder> {
    return this.http.get<MerchantOrder>(`${MERCHANT_ORDERS_API}/${encodeURIComponent(id)}`);
  }
}

export type MerchantUrgency = 'NORMAL' | 'URGENT';
export type MerchantPaymentMethod = 'ONLINE' | 'COD';

export interface MerchantOrderAddress {
  contactName: string;
  contactPhone: string;
  streetAddress: string;
  city: string;
  postalCode?: string;
  zoneCode?: string;
}

/** Payload for POST /api/merchant/orders (MerchantOrderRequest in the backend). */
export interface CreateMerchantOrderRequest {
  merchantReference?: string;
  description?: string;
  weightKg: number;
  urgency: MerchantUrgency;
  urgentNote?: string;
  paymentMethod: MerchantPaymentMethod;
  /** Cash to collect from the recipient (LKR). Required for COD. */
  codAmount?: number;
  pickupAddress: MerchantOrderAddress;
  /** zoneCode is required: it sets the zone charge. */
  deliveryAddress: MerchantOrderAddress;
}

/** MerchantOrderResponse in the backend. `id` is the booking id used by receipts and cancellation. */
export interface MerchantOrder {
  id: string;
  trackingNumber: string;
  merchantId: string;
  merchantReference?: string | null;
  description?: string | null;
  weightKg: number;
  urgency: MerchantUrgency;
  urgentNote?: string | null;
  paymentMethod: MerchantPaymentMethod;
  paymentStatus: string;
  codAmount?: number | null;
  deliveryFee: number;
  currency: string;
  feeBreakdown: {
    baseFee: number;
    weightCharge: number;
    zoneCharge: number;
    zoneCode: string;
    urgentSurcharge: number;
    total: number;
    pricingRuleCode: string;
  };
  status: string;
  source: 'DASHBOARD' | 'EXTERNAL_API';
  receiptNumber?: string | null;
  pickupAddress: MerchantOrderAddress;
  deliveryAddress: MerchantOrderAddress;
  createdAt: string;
  updatedAt: string;
}
