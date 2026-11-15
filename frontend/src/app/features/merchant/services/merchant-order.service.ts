import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service for interacting with merchant order related backend endpoints.
 * Provides order CRUD, dashboard data, and a simple fee calculator used by the UI.
 */
@Injectable({
  providedIn: 'root',
})
export class MerchantOrderService {
  private readonly baseUrl = '/api/merchant/orders';

  constructor(private http: HttpClient) {}

  /** Simple fee calculation (mock) based on weight and urgency.
   * Returns a breakdown used by CreateOrderComponent.
   */
  calculateFee(weightKg: number, urgency: 'NORMAL' | 'URGENT'): FeeBreakdown {
    const baseFee = 50; // base fee in LKR
    const weightRate = 20; // per kg
    const urgentSurcharge = urgency === 'URGENT' ? 30 : 0;
    const weightFee = weightKg * weightRate;
    const totalFee = baseFee + weightFee + urgentSurcharge;
    return { baseFee, weightFee, urgencySurcharge: urgentSurcharge, totalFee };
  }

  /** Retrieves a list of orders belonging to the currently authenticated merchant. */
  getOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(this.baseUrl);
  }

  /** Creates a new order for the merchant. */
  createOrder(request: CreateMerchantOrderRequest): Observable<any> {
    return this.http.post<any>(this.baseUrl, request);
  }

  /** Retrieves dashboard summary and recent orders. */
  getDashboardData(): Observable<{ summary: Summary; recentOrders: Order[] }> {
    return this.http.get<{ summary: Summary; recentOrders: Order[] }>(`${this.baseUrl}/dashboard`);
  }
}

/** Order representation returned by the backend. Adjust fields to match the actual API response. */
export interface Order {
  id: string;
  createdAt: string; // ISO date string
  deliveryAddress: string;
  fee: number; // total fee in LKR
  status: string; // e.g., 'PENDING', 'IN_TRANSIT', 'DELIVERED'
  recipientName?: string;
  // Optional aliases for compatibility with existing UI components
  receiverName?: string;
  deliveryLocation?: string;
  orderDate?: string;
}

/** Payload for creating a new merchant order. */
export interface CreateMerchantOrderRequest {
  description?: string;
  weightKg: number;
  urgency: 'NORMAL' | 'URGENT';
  pickupAddress: {
    contactName: string;
    contactPhone: string;
    streetAddress: string;
    city: string;
    postalCode?: string;
    zoneCode?: string;
  };
  deliveryAddress: {
    contactName: string;
    contactPhone: string;
    streetAddress: string;
    city: string;
    postalCode?: string;
    zoneCode?: string;
  };
}

/** Fee breakdown interface used by CreateOrderComponent */
export interface FeeBreakdown {
  baseFee: number;
  weightFee: number;
  urgencySurcharge: number;
  totalFee: number;
}

/** Summary interface for merchant dashboard */
export interface Summary {
  totalOrders: number;
  pendingOrders: number;
  deliveredOrders: number;
}
