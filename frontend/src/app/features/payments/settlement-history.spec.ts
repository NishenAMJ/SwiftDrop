import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MerchantFinancePage } from './merchant-finance-page/merchant-finance-page';
import { SettlementOperationsPage } from './settlement-operations-page/settlement-operations-page';

describe('Settlement history', () => {
  let http: HttpTestingController;
  const merchantId = '846c790e-497a-4340-8039-79e25c95f40e';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('shows history failures separately from an empty merchant history', () => {
    const fixture = TestBed.createComponent(MerchantFinancePage);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/merchant-ledger/entries'))
      .flush({ content: [], page: 0, totalPages: 0, totalElements: 0 });
    const request = http.expectOne((r) => r.url.endsWith('/merchant-settlements'));
    expect(request.request.params.has('merchantId')).toBe(false);
    request.flush({}, { status: 500, statusText: 'Server error' });
    fixture.detectChanges();
    const history = fixture.nativeElement.querySelector('[aria-labelledby="settlement-history-title"]');
    expect(history.textContent).toContain('Your settlement history could not be loaded');
    expect(history.textContent).not.toContain('No settlements have been calculated');
  });

  it('preserves operation feedback and resets paging when changing merchants', () => {
    const fixture = TestBed.createComponent(SettlementOperationsPage);
    const page = fixture.componentInstance;
    page['notice'].set('Settlement calculated.');
    page['settlementHistoryForm'].controls.merchantId.setValue(merchantId);
    page['loadSettlementHistory'](3);
    const request = http.expectOne((r) => r.url.endsWith('/merchant-settlements'));
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('merchantId')).toBe(merchantId);
    expect(page['notice']()).toBe('Settlement calculated.');
    expect(page['loading']()).toBe(false);
    expect(page['historyLoading']()).toBe(true);
    request.flush({}, { status: 500, statusText: 'Server error' });
    expect(page['settlements']()).toBeNull();
    expect(page['historyLoading']()).toBe(false);
    expect(page['historyError']()).toContain('could not be loaded');
  });
});
