import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Page } from '../../../core/auth/auth.models';
import { problem } from '../../../testing/auth-fixtures';
import { Merchant } from '../../merchant/merchant.models';
import { MerchantReviewPage } from './merchant-review-page';

function merchant(id: string, extra: Partial<Merchant> = {}): Merchant {
  return {
    id,
    userId: `user-${id}`,
    businessName: `Shop ${id}`,
    businessType: 'INDIVIDUAL',
    contactPhone: '+94112345678',
    businessAddress: { line1: '12 Galle Road', city: 'Colombo' },
    status: 'PENDING',
    submittedAt: '2026-11-10T04:30:00Z',
    createdAt: '2026-11-10T04:30:00Z',
    ...extra,
  };
}

function pageOf(content: Merchant[], extra: Partial<Page<Merchant>> = {}): Page<Merchant> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra };
}

describe('MerchantReviewPage', () => {
  let fixture: ComponentFixture<MerchantReviewPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  const isList = (request: { url: string; method: string }) =>
    request.method === 'GET' && request.url === '/api/v1/merchants';

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [MerchantReviewPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MerchantReviewPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function flushList(result: Page<Merchant>): Promise<TestRequest> {
    const request = http.expectOne(isList);
    request.flush(result);
    await fixture.whenStable();
    return request;
  }

  function button(text: string, root: ParentNode = page): HTMLButtonElement {
    return [...root.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
  }

  async function confirmWith(text: string | null) {
    const dialog = page.querySelector('dialog')!;
    if (text !== null) {
      const box = dialog.querySelector('textarea')!;
      box.value = text;
      box.dispatchEvent(new Event('input'));
    }
    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('opens on the pending queue, oldest first', async () => {
    const request = await flushList(pageOf([merchant('1'), merchant('2')]));
    expect(request.request.params.get('status')).toBe('PENDING');
    expect(request.request.params.has('q')).toBe(false);

    expect([...page.querySelectorAll('.review-item h3')].map((h) => h.textContent?.trim())).toEqual(['Shop 1', 'Shop 2']);
    expect(page.textContent).toContain('(2 applications)');
    expect(button('Approve').textContent).toContain('Shop 1');
    expect(button('Reject')).toBeDefined();
  });

  it('says when nothing is waiting', async () => {
    await flushList(pageOf([]));
    expect(page.textContent).toContain('No merchant applications are waiting for review');
  });

  it('approves with an optional internal note, then reloads', async () => {
    await flushList(pageOf([merchant('1')]));
    button('Approve').click();
    await fixture.whenStable();
    expect(page.querySelector('dialog')!.textContent).toContain('can create API keys');

    await confirmWith('Checked registration');
    const decision = http.expectOne('/api/v1/merchants/1/approve');
    expect(decision.request.body).toEqual({ note: 'Checked registration' });
    decision.flush(merchant('1', { status: 'APPROVED' }));
    await flushList(pageOf([]));

    expect(page.querySelector('.sd-alert--success')?.textContent).toContain('Shop 1: approved.');
    expect(document.activeElement?.id).toBe('merchant-results-title');
  });

  it('needs a reason to reject, and sends it', async () => {
    await flushList(pageOf([merchant('1')]));
    button('Reject').click();
    await fixture.whenStable();

    await confirmWith(null);
    http.expectNone('/api/v1/merchants/1/reject');
    const box = page.querySelector('dialog textarea')!;
    expect(box.getAttribute('aria-invalid')).toBe('true');
    expect(page.querySelector('dialog .sd-error')?.textContent).toContain('Enter a reason');

    await confirmWith('Registration number not found.');
    const decision = http.expectOne('/api/v1/merchants/1/reject');
    expect(decision.request.body).toEqual({ reason: 'Registration number not found.' });
    decision.flush(merchant('1', { status: 'REJECTED' }));
    await flushList(pageOf([]));
  });

  it('offers only the decisions allowed from each status', async () => {
    page.querySelector<HTMLSelectElement>('#merchant-filter-status')!.value = '';
    page.querySelector('#merchant-filter-status')!.dispatchEvent(new Event('change'));
    await flushList(pageOf([]));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = await flushList(
      pageOf([
        merchant('a', { status: 'APPROVED' }),
        merchant('s', { status: 'SUSPENDED', lastDecision: { status: 'SUSPENDED', reason: 'Fraud report', decidedAt: '2026-11-12T00:00:00Z' } }),
        merchant('r', { status: 'REJECTED' }),
      ]),
    );
    expect(request.request.params.has('status')).toBe(false);

    const actions = [...page.querySelectorAll('.review-item')].map((item) =>
      [...item.querySelectorAll('.review-item__actions button')].map((b) => b.textContent?.trim().split(' ')[0]),
    );
    expect(actions).toEqual([['Suspend'], ['Reactivate'], []]);
    expect(page.textContent).toContain('Fraud report');

    // API keys exist only for merchants that were approved at some point.
    const keyToggles = [...page.querySelectorAll('.review-item')].map((item) => !!item.querySelector('app-merchant-api-keys'));
    expect(keyToggles).toEqual([true, true, false]);
  });

  it('reloads instead of failing when another dispatcher decided first', async () => {
    await flushList(pageOf([merchant('1')]));
    button('Approve').click();
    await fixture.whenStable();
    await confirmWith('');

    http
      .expectOne('/api/v1/merchants/1/approve')
      .flush(problem('INVALID_STATUS_TRANSITION', { status: 409 }), { status: 409, statusText: 'Conflict' });
    await flushList(pageOf([]));

    expect(page.querySelector('dialog')!.hasAttribute('open')).toBe(false);
    expect(page.querySelector('.sd-alert--info')?.textContent).toContain('changed by someone else');
  });
});
