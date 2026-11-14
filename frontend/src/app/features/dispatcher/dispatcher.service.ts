import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AuthUser, Page, Role } from '../../core/auth/auth.models';
import { ApplicationDecision, ApplicationStatus } from '../../shared/models/application';
import { Courier, VehicleType } from '../courier/courier.models';
import { ApiKey, Merchant } from '../merchant/merchant.models';

export interface PageQuery {
  page?: number;
  size?: number;
}

export interface MerchantQuery extends PageQuery {
  status?: ApplicationStatus;
  q?: string;
}

export interface CourierQuery extends PageQuery {
  status?: ApplicationStatus;
  vehicleType?: VehicleType;
}

export interface UserQuery extends PageQuery {
  role?: Role;
  status?: AuthUser['status'];
  q?: string;
}

export interface CreateDispatcherRequest {
  email: string;
  fullName: string;
  phoneNumber?: string;
  initialPassword: string;
}

/**
 * Body of a decision. Reject and suspend need a `reason` (shown to the applicant); approve and
 * reactivate take an optional internal `note` (dispatchers only).
 */
export type DecisionBody = { reason: string } | { note?: string };

/** Dispatcher tools of identity-merchant-service: applications, users and dispatcher accounts. */
@Injectable({ providedIn: 'root' })
export class DispatcherService {
  private readonly http = inject(HttpClient);

  /** Oldest submission first by default, so a queue reads top-down. */
  listMerchants(query: MerchantQuery): Observable<Page<Merchant>> {
    return this.http.get<Page<Merchant>>('/api/v1/merchants', { params: toParams(query) });
  }

  decideMerchant(merchantId: string, decision: ApplicationDecision, body: DecisionBody): Observable<Merchant> {
    return this.http.post<Merchant>(
      `/api/v1/merchants/${encodeURIComponent(merchantId)}/${decision}`,
      body,
    );
  }

  /** A merchant's API keys (prefixes only), newest first. */
  listMerchantApiKeys(merchantId: string): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(`/api/v1/merchants/${encodeURIComponent(merchantId)}/api-keys`);
  }

  revokeMerchantApiKey(merchantId: string, keyId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/v1/merchants/${encodeURIComponent(merchantId)}/api-keys/${encodeURIComponent(keyId)}`,
    );
  }

  listCouriers(query: CourierQuery): Observable<Page<Courier>> {
    return this.http.get<Page<Courier>>('/api/v1/couriers', { params: toParams(query) });
  }

  decideCourier(userId: string, decision: ApplicationDecision, body: DecisionBody): Observable<Courier> {
    return this.http.post<Courier>(`/api/v1/couriers/${encodeURIComponent(userId)}/${decision}`, body);
  }

  /** Newest accounts first by default. */
  listUsers(query: UserQuery): Observable<Page<AuthUser>> {
    return this.http.get<Page<AuthUser>>('/api/v1/users', { params: toParams(query) });
  }

  createDispatcher(request: CreateDispatcherRequest): Observable<AuthUser> {
    return this.http.post<AuthUser>('/api/v1/dispatchers', request);
  }
}

/** Leaves out empty filters, so the server applies its own defaults. */
function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
