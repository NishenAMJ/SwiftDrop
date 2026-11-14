import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { problem } from '../../../testing/auth-fixtures';
import { ApiKey } from '../../merchant/merchant.models';
import { MerchantApiKeys } from './merchant-api-keys';

const KEYS_URL = '/api/v1/merchants/m1/api-keys';

function key(id: string, extra: Partial<ApiKey> = {}): ApiKey {
  return { id, name: `Key ${id}`, prefix: `sd_live_${id}`, createdAt: '2026-11-10T04:30:00Z', ...extra };
}

describe('MerchantApiKeys', () => {
  let fixture: ComponentFixture<MerchantApiKeys>;
  let http: HttpTestingController;
  let host: HTMLElement;

  async function create(status: 'APPROVED' | 'SUSPENDED' = 'APPROVED') {
    TestBed.configureTestingModule({
      imports: [MerchantApiKeys],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MerchantApiKeys);
    fixture.componentRef.setInput('merchantId', 'm1');
    fixture.componentRef.setInput('businessName', 'Shop 1');
    fixture.componentRef.setInput('status', status);
    host = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  function button(text: string): HTMLButtonElement {
    return [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
  }

  async function open(keys: ApiKey[]) {
    button('Show API keys').click();
    await fixture.whenStable();
    http.expectOne(KEYS_URL).flush(keys);
    await fixture.whenStable();
  }

  it('loads nothing until opened, then lists keys without secrets', async () => {
    await create();
    http.expectNone(KEYS_URL);
    const toggle = button('Show API keys');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.textContent).toContain('of Shop 1');

    await open([key('a'), key('b', { revokedAt: '2026-11-11T00:00:00Z' }), key('c', { expiresAt: '2020-01-01T00:00:00Z' })]);

    expect(button('Hide API keys').getAttribute('aria-expanded')).toBe('true');
    const rows = [...host.querySelectorAll('tbody tr')].map((row) => row.textContent ?? '');
    expect(rows[0]).toContain('sd_live_a');
    expect(rows[0]).toContain('Active');
    expect(rows[1]).toContain('Revoked');
    expect(rows[2]).toContain('Expired');
    // Revoked keys can't be revoked again.
    expect(host.querySelectorAll('tbody button').length).toBe(2);
  });

  it('says when the merchant has no keys, and warns while suspended', async () => {
    await create('SUSPENDED');
    await open([]);
    expect(host.textContent).toContain("hasn't created any API keys");
    expect(host.querySelector('.sd-alert--info')?.textContent).toContain('none of its keys work');
  });

  it('revokes one key after confirming, then reloads', async () => {
    await create();
    await open([key('a')]);

    button('Revoke').click();
    await fixture.whenStable();
    const dialog = host.querySelector('dialog')!;
    expect(dialog.textContent).toContain('other keys keep working');

    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    const revoke = http.expectOne(`${KEYS_URL}/a`);
    expect(revoke.request.method).toBe('DELETE');
    revoke.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    http.expectOne(KEYS_URL).flush([key('a', { revokedAt: '2026-11-15T00:00:00Z' })]);
    await fixture.whenStable();

    expect(host.querySelector('.sd-alert--success')?.textContent).toContain('Key "Key a" is revoked');
    expect(host.querySelector('tbody tr')?.textContent).toContain('Revoked');
  });

  it('refreshes instead of failing when the key is already gone', async () => {
    await create();
    await open([key('a')]);
    button('Revoke').click();
    await fixture.whenStable();
    host.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectOne(`${KEYS_URL}/a`).flush(problem('NOT_FOUND', { status: 404 }), { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    http.expectOne(KEYS_URL).flush([]);
    await fixture.whenStable();

    expect(host.querySelector('dialog')!.hasAttribute('open')).toBe(false);
    expect(host.textContent).toContain('no longer exists');
  });

  it('shows a readable error with a retry when loading fails', async () => {
    await create();
    button('Show API keys').click();
    await fixture.whenStable();
    http.expectOne(KEYS_URL).flush(problem('INTERNAL', { status: 500 }), { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(host.querySelector('[role="alert"]')).not.toBeNull();
    button('Try again').click();
    await fixture.whenStable();
    http.expectOne(KEYS_URL).flush([]);
  });
});
