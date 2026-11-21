import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EvidenceType, Payment, PaymentPage, RecoveryAudit, Refund } from './payment.models';

const API = '/api/v1/payments';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);

  history(page = 0, size = 20): Observable<PaymentPage> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'createdAt,desc');
    return this.http.get<PaymentPage>(`${API}/history`, { params });
  }

  get(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${API}/${encodeURIComponent(paymentId)}`);
  }

  refund(paymentId: string, amount: number, reason: string, idempotencyKey: string): Observable<Refund> {
    return this.http.post<Refund>(
      `${API}/${encodeURIComponent(paymentId)}/refund`,
      { amount, reason },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  refundStatus(paymentId: string, refundId: string): Observable<Refund> {
    return this.http.get<Refund>(
      `${API}/${encodeURIComponent(paymentId)}/refunds/${encodeURIComponent(refundId)}`,
    );
  }

  retryRefund(paymentId: string, refundId: string): Observable<Refund> {
    return this.http.post<Refund>(
      `${API}/${encodeURIComponent(paymentId)}/refunds/${encodeURIComponent(refundId)}/retry`,
      { confirmNoRefundAtProvider: true },
    );
  }

  resolveRefund(
    paymentId: string,
    refundId: string,
    request: {
      resolution: 'CONFIRMED_SUCCESS' | 'CONFIRMED_FAILURE';
      evidenceType: EvidenceType;
      evidenceReference: string;
      providerRefundReference?: string;
      reason: string;
      confirmProviderEvidence: true;
    },
  ): Observable<Refund> {
    return this.http.post<Refund>(
      `${API}/${encodeURIComponent(paymentId)}/refunds/${encodeURIComponent(refundId)}/resolve`,
      request,
    );
  }

  releaseBookingClaim(
    paymentId: string,
    request: {
      confirmProviderTerminalFailure: true;
      evidenceType: EvidenceType;
      evidenceReference: string;
      reason: string;
    },
  ): Observable<void> {
    return this.http.post<void>(
      `${API}/${encodeURIComponent(paymentId)}/booking-claim/release`,
      request,
    );
  }

  recoveryAudit(paymentId: string): Observable<RecoveryAudit[]> {
    return this.http.get<RecoveryAudit[]>(
      `${API}/${encodeURIComponent(paymentId)}/recovery-audit`,
    );
  }
}
