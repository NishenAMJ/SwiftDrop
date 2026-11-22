import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentService } from './payment.service';

describe('PaymentService', () => {
  let service: PaymentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PaymentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests history newest first with paging', () => {
    service.history(3, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/payments/history');
    expect(req.request.params.get('page')).toBe('3');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    req.flush({ content: [] });
  });

  it('sends refunds with the idempotency key and never retries without provider confirmation', () => {
    service.refund('pay/1', 250.5, 'Damaged', 'refund-key').subscribe();
    const refund = http.expectOne('/api/v1/payments/pay%2F1/refund');
    expect(refund.request.method).toBe('POST');
    expect(refund.request.body).toEqual({ amount: 250.5, reason: 'Damaged' });
    expect(refund.request.headers.get('Idempotency-Key')).toBe('refund-key');
    refund.flush({});

    service.retryRefund('p1', 'r1').subscribe();
    const retry = http.expectOne('/api/v1/payments/p1/refunds/r1/retry');
    expect(retry.request.body).toEqual({ confirmNoRefundAtProvider: true });
    retry.flush({});
  });

  it('uses encoded ids for every payment read and recovery action', () => {
    service.get('a b').subscribe();
    http.expectOne('/api/v1/payments/a%20b').flush({});
    service.refundStatus('p 1', 'r/2').subscribe();
    http.expectOne('/api/v1/payments/p%201/refunds/r%2F2').flush({});
    service.recoveryAudit('p1').subscribe();
    http.expectOne('/api/v1/payments/p1/recovery-audit').flush([]);
    service.cancellations(2).subscribe();
    const cancellations = http.expectOne((r) => r.url === '/api/v1/payments/cancellations');
    expect(cancellations.request.params.get('page')).toBe('2');
    cancellations.flush({ content: [] });
  });

  it('posts resolutions and claim releases with the operator evidence', () => {
    const resolution = {
      resolution: 'CONFIRMED_FAILURE' as const,
      evidenceType: 'PAYHERE_SUPPORT_CASE' as const,
      evidenceReference: 'case-1',
      reason: 'Rejected',
      confirmProviderEvidence: true as const,
    };
    service.resolveRefund('p1', 'r1', resolution).subscribe();
    expect(http.expectOne('/api/v1/payments/p1/refunds/r1/resolve').request.body).toEqual(resolution);

    const release = {
      confirmProviderTerminalFailure: true as const,
      evidenceType: 'PAYHERE_DASHBOARD' as const,
      evidenceReference: 'dash-1',
      reason: 'Declined',
    };
    service.releaseBookingClaim('p1', release).subscribe();
    expect(http.expectOne('/api/v1/payments/p1/booking-claim/release').request.body).toEqual(release);
  });
});
