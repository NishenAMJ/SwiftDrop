import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CodSettlementService } from './cod-settlement.service';

describe('CodSettlementService', () => {
  let service: CodSettlementService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CodSettlementService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists handovers with default paging and only the filters that were given', () => {
    service.listHandovers().subscribe();
    const plain = http.expectOne((r) => r.url === '/api/v1/cod-handovers');
    expect(plain.request.params.get('page')).toBe('0');
    expect(plain.request.params.get('size')).toBe('20');
    expect(plain.request.params.has('courierId')).toBe(false);
    expect(plain.request.params.has('status')).toBe(false);
    plain.flush({ content: [] });

    service.listHandovers({ page: 2, size: 5, courierId: '  courier-1 ', status: 'FLAGGED' }).subscribe();
    const filtered = http.expectOne((r) => r.url === '/api/v1/cod-handovers' && r.params.get('page') === '2');
    expect(filtered.request.params.get('courierId')).toBe('courier-1');
    expect(filtered.request.params.get('status')).toBe('FLAGGED');
    filtered.flush({ content: [] });
  });

  it('ignores a blank courier filter', () => {
    service.listHandovers({ courierId: '   ' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/cod-handovers');
    expect(req.request.params.has('courierId')).toBe(false);
    req.flush({ content: [] });
  });

  it('sends the idempotency key with every money-moving command', () => {
    service.createHandover({ collectionIds: ['c1'], submittedAmount: 1000, currency: 'LKR' }, 'key-handover').subscribe();
    const create = http.expectOne('/api/v1/cod-handovers');
    expect(create.request.method).toBe('POST');
    expect(create.request.headers.get('Idempotency-Key')).toBe('key-handover');
    create.flush({});

    service.confirmHandover('h/1', { receivedAmount: 900, investigationNote: 'Short' }, 'key-confirm').subscribe();
    const confirm = http.expectOne('/api/v1/cod-handovers/h%2F1/confirm');
    expect(confirm.request.headers.get('Idempotency-Key')).toBe('key-confirm');
    expect(confirm.request.body).toEqual({ receivedAmount: 900, investigationNote: 'Short' });
    confirm.flush({});

    service.createAdjustment(
      { merchantId: 'm1', amount: -100, currency: 'LKR', sourceReference: 'case-1', reason: 'Fix' },
      'key-adjust',
    ).subscribe();
    const adjust = http.expectOne('/api/v1/merchant-ledger/adjustments');
    expect(adjust.request.headers.get('Idempotency-Key')).toBe('key-adjust');
    adjust.flush({});

    service.calculateSettlement({ merchantId: 'm1', currency: 'LKR', cutoffAt: '2026-11-22T00:00:00Z' }, 'key-calc').subscribe();
    const calc = http.expectOne('/api/v1/merchant-settlements/calculate');
    expect(calc.request.headers.get('Idempotency-Key')).toBe('key-calc');
    calc.flush({});
  });

  it('always confirms the calculated amount on approval and only sends a note when given', () => {
    service.approveSettlement('s1', '', 'key-a').subscribe();
    const withoutNote = http.expectOne('/api/v1/merchant-settlements/s1/approve');
    expect(withoutNote.request.body).toEqual({ confirmCalculatedAmount: true });
    withoutNote.flush({});

    service.approveSettlement('s1', 'Checked ledger', 'key-b').subscribe();
    const withNote = http.expectOne('/api/v1/merchant-settlements/s1/approve');
    expect(withNote.request.body).toEqual({ confirmCalculatedAmount: true, note: 'Checked ledger' });
    withNote.flush({});
  });

  it('records an external payment with explicit confirmation', () => {
    service.recordSettlementPayment('s1', { externalReference: 'bank-1', paidAt: '2026-11-22T10:00:00Z' }, 'key-p').subscribe();
    const req = http.expectOne('/api/v1/merchant-settlements/s1/record-payment');
    expect(req.request.body).toEqual({ externalReference: 'bank-1', paidAt: '2026-11-22T10:00:00Z', confirmExternalPayment: true });
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-p');
    req.flush({});
  });

  it('normalises ledger filters', () => {
    service.listLedger({ merchantId: ' m1 ', currency: ' lkr ' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/merchant-ledger/entries');
    expect(req.request.params.get('merchantId')).toBe('m1');
    expect(req.request.params.get('currency')).toBe('LKR');
    req.flush({ content: [] });

    service.listLedger().subscribe();
    const bare = http.expectOne((r) => r.url === '/api/v1/merchant-ledger/entries');
    expect(bare.request.params.has('currency')).toBe(false);
    bare.flush({ content: [] });
  });

  it('reads single handovers and settlements with encoded ids', () => {
    service.getHandover('a b').subscribe();
    http.expectOne('/api/v1/cod-handovers/a%20b').flush({});
    service.getSettlement('x/y').subscribe();
    http.expectOne('/api/v1/merchant-settlements/x%2Fy').flush({});
    service.listSettlements({ merchantId: ' m2 ' }).subscribe();
    const list = http.expectOne((r) => r.url === '/api/v1/merchant-settlements');
    expect(list.request.params.get('merchantId')).toBe('m2');
    list.flush({ content: [] });
  });
});
