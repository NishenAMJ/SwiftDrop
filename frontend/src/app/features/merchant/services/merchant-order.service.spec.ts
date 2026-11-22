import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MERCHANT_ORDERS_API, MerchantOrderService } from './merchant-order.service';

describe('MerchantOrderService', () => {
  let service: MerchantOrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MerchantOrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the total from X-Total-Count', () => {
    let result: { total: number } | undefined;
    service.getOrders(1, 10).subscribe((r) => (result = r));
    const req = http.expectOne((r) => r.url === MERCHANT_ORDERS_API);
    expect(req.request.params.get('page')).toBe('1');
    req.flush([{ id: 'o1' }], { headers: { 'X-Total-Count': '42' } });
    expect(result?.total).toBe(42);
  });

  it('falls back to the page length, and to zero for an empty body', () => {
    let result: { orders: unknown[]; total: number } | undefined;
    service.getOrders().subscribe((r) => (result = r));
    http.expectOne((r) => r.url === MERCHANT_ORDERS_API).flush([{ id: 'a' }, { id: 'b' }]);
    expect(result?.total).toBe(2);

    service.getOrders().subscribe((r) => (result = r));
    http.expectOne((r) => r.url === MERCHANT_ORDERS_API).flush(null);
    expect(result).toEqual({ orders: [], total: 0 });
  });

  it('creates orders and reads one with an encoded id', () => {
    service.createOrder({} as never).subscribe();
    expect(http.expectOne(MERCHANT_ORDERS_API).request.method).toBe('POST');
    service.getOrder('a/b').subscribe();
    http.expectOne(`${MERCHANT_ORDERS_API}/a%2Fb`).flush({});
  });
});
