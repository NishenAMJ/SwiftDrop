import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ApiKey,
  CreateApiKeyRequest,
  CreatedApiKey,
  Merchant,
  MerchantApplicationRequest,
} from './merchant.models';

const MERCHANTS_API = '/api/v1/merchants';

/** The signed-in user's own merchant application and API keys (identity-merchant-service). */
@Injectable({ providedIn: 'root' })
export class MerchantService {
  private readonly http = inject(HttpClient);

  /** The user's application in any status; 404 when they have never applied. */
  me(): Observable<Merchant> {
    return this.http.get<Merchant>(`${MERCHANTS_API}/me`);
  }

  /** First application, or resubmission after a rejection (same endpoint). */
  apply(request: MerchantApplicationRequest): Observable<Merchant> {
    return this.http.post<Merchant>(MERCHANTS_API, request);
  }

  listApiKeys(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(`${MERCHANTS_API}/me/api-keys`);
  }

  createApiKey(request: CreateApiKeyRequest): Observable<CreatedApiKey> {
    return this.http.post<CreatedApiKey>(`${MERCHANTS_API}/me/api-keys`, request);
  }

  revokeApiKey(keyId: string): Observable<void> {
    return this.http.delete<void>(`${MERCHANTS_API}/me/api-keys/${encodeURIComponent(keyId)}`);
  }
}
