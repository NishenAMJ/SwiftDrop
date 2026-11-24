import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CashHandover,
  CashHandoverPage,
  HandoverStatus,
  MerchantLedgerEntry,
  MerchantLedgerPage,
  MerchantSettlement,
  MerchantSettlementPage,
} from './cod-settlement.models';

const HANDOVERS = '/api/v1/cod-handovers';
const LEDGER = '/api/v1/merchant-ledger';
const SETTLEMENTS = '/api/v1/merchant-settlements';
const id = encodeURIComponent;

@Injectable({ providedIn: 'root' })
export class CodSettlementService {
  private readonly http = inject(HttpClient);

  listHandovers(
    filters: {
      page?: number;
      size?: number;
      courierId?: string;
      status?: HandoverStatus | '';
    } = {},
  ): Observable<CashHandoverPage> {
    let params = new HttpParams().set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    if (filters.courierId?.trim()) params = params.set('courierId', filters.courierId.trim());
    if (filters.status) params = params.set('status', filters.status);
    return this.http.get<CashHandoverPage>(HANDOVERS, { params });
  }

  createHandover(
    request: {
      collectionIds: string[];
      submittedAmount: number;
      currency: string;
    },
    idempotencyKey: string,
  ): Observable<CashHandover> {
    return this.http.post<CashHandover>(HANDOVERS, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  getHandover(handoverId: string): Observable<CashHandover> {
    return this.http.get<CashHandover>(`${HANDOVERS}/${id(handoverId)}`);
  }

  confirmHandover(
    handoverId: string,
    request: {
      receivedAmount: number;
      investigationNote?: string;
    },
    idempotencyKey: string,
  ): Observable<CashHandover> {
    return this.http.post<CashHandover>(`${HANDOVERS}/${id(handoverId)}/confirm`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  listLedger(
    filters: {
      merchantId?: string;
      currency?: string;
      page?: number;
      size?: number;
    } = {},
  ): Observable<MerchantLedgerPage> {
    let params = new HttpParams().set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    if (filters.merchantId?.trim()) params = params.set('merchantId', filters.merchantId.trim());
    if (filters.currency?.trim())
      params = params.set('currency', filters.currency.trim().toUpperCase());
    return this.http.get<MerchantLedgerPage>(`${LEDGER}/entries`, { params });
  }

  createAdjustment(
    request: {
      merchantId: string;
      amount: number;
      currency: string;
      sourceReference: string;
      reason: string;
    },
    idempotencyKey: string,
  ): Observable<MerchantLedgerEntry> {
    return this.http.post<MerchantLedgerEntry>(`${LEDGER}/adjustments`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  calculateSettlement(
    request: {
      merchantId: string;
      currency: string;
      cutoffAt: string;
    },
    idempotencyKey: string,
  ): Observable<MerchantSettlement> {
    return this.http.post<MerchantSettlement>(`${SETTLEMENTS}/calculate`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  getSettlement(settlementId: string): Observable<MerchantSettlement> {
    return this.http.get<MerchantSettlement>(`${SETTLEMENTS}/${id(settlementId)}`);
  }

  listSettlements(
    filters: { merchantId?: string; page?: number; size?: number } = {},
  ): Observable<MerchantSettlementPage> {
    let params = new HttpParams().set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    if (filters.merchantId?.trim()) params = params.set('merchantId', filters.merchantId.trim());
    return this.http.get<MerchantSettlementPage>(SETTLEMENTS, { params });
  }

  approveSettlement(
    settlementId: string,
    note: string,
    idempotencyKey: string,
  ): Observable<MerchantSettlement> {
    return this.http.post<MerchantSettlement>(
      `${SETTLEMENTS}/${id(settlementId)}/approve`,
      {
        confirmCalculatedAmount: true,
        ...(note ? { note } : {}),
      },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  recordSettlementPayment(
    settlementId: string,
    request: {
      externalReference: string;
      paidAt: string;
    },
    idempotencyKey: string,
  ): Observable<MerchantSettlement> {
    return this.http.post<MerchantSettlement>(
      `${SETTLEMENTS}/${id(settlementId)}/record-payment`,
      {
        ...request,
        confirmExternalPayment: true,
      },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }
}
