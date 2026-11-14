import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { problem } from '../../../testing/auth-fixtures';
import { ApiKey } from '../merchant.models';
import { ApiKeysPage, colomboToday, endOfColomboDay } from './api-keys-page';

const KEYS_API = '/api/v1/merchants/me/api-keys';

function key(id: string, extra: Partial<ApiKey> = {}): ApiKey {
  return { id, name: `Key ${id}`, prefix: 'sd_live_7Kq2', createdAt: '2026-11-01T04:30:00Z', ...extra };
}

describe('colombo dates', () => {
  it('uses the Sri Lankan calendar day, not the device time zone', () => {
    // 20:00 UTC is already 01:30 the next day in Colombo.
    expect(colomboToday(new Date('2026-11-14T20:00:00Z'))).toBe('2026-11-15');
    expect(endOfColomboDay('2026-12-31')).toBe('2026-12-31T18:29:59.000Z');
  });
});

describe('ApiKeysPage', () => {
  let fixture: ComponentFixture<ApiKeysPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  async function open(keys: ApiKey[]) {
    TestBed.configureTestingModule({
      imports: [ApiKeysPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ApiKeysPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    http.expectOne(KEYS_API).flush(keys);
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  function type(id: string, value: string) {
    const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function button(text: string): HTMLButtonElement {
    return [...page.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
  }

  it('lists keys by prefix with their status in words', async () => {
    await open([key('1'), key('2', { revokedAt: '2026-11-05T00:00:00Z' }), key('3', { expiresAt: '2026-11-02T00:00:00Z' })]);

    const rows = [...page.querySelectorAll('tbody tr')];
    expect(rows.map((row) => row.querySelector('.sd-badge')?.textContent?.trim())).toEqual([
      'Active',
      'Revoked',
      'Expired',
    ]);
    expect(rows[0].textContent).toContain('sd_live_7Kq2…');
    // Revoked keys can't be revoked again.
    expect(rows[1].querySelector('button')).toBeNull();
    expect(page.textContent).toContain('1 of 5 active keys used');
  });

  it('explains that there are no keys yet', async () => {
    await open([]);
    expect(page.textContent).toContain("You haven't created any keys yet");
  });

  it('shows a new key once, and forgets it when the merchant has saved it', async () => {
    await open([]);
    type('key-name', ' Shopify store ');
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = http.expectOne(KEYS_API);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Shopify store', expiresAt: undefined });
    request.flush(
      { ...key('9', { name: 'Shopify store' }), key: 'sd_live_7Kq2xR9mT4vB8nW1pL6cH3jD5fG0sA2e' },
      { status: 201, statusText: 'Created' },
    );
    await fixture.whenStable();

    const shown = page.querySelector<HTMLInputElement>('#new-key-value')!;
    expect(shown.value).toBe('sd_live_7Kq2xR9mT4vB8nW1pL6cH3jD5fG0sA2e');
    expect(shown.readOnly).toBe(true);
    expect(page.textContent).toContain('only time the full key is shown');
    expect(document.activeElement?.id).toBe('new-key-title');
    // The table never holds the secret.
    expect(page.querySelector('tbody')?.textContent).not.toContain('xR9mT4');

    button("I've saved the key").click();
    await fixture.whenStable();
    expect(page.querySelector('#new-key-value')).toBeNull();
    expect(page.textContent).not.toContain('sd_live_7Kq2xR9mT4');
  });

  it('stops creating keys at the limit and says why', async () => {
    await open(['1', '2', '3', '4', '5'].map((id) => key(id)));
    expect(page.textContent).toContain('You have 5 active keys, the most allowed');
    expect(button('Create key').disabled).toBe(true);
  });

  it('revokes a key after confirming what will happen', async () => {
    await open([key('1', { name: 'Shopify store' })]);
    button('Revoke').click();
    await fixture.whenStable();

    const dialog = page.querySelector('dialog')!;
    expect(dialog.hasAttribute('open')).toBe(true);
    expect(dialog.textContent).toContain('Revoke Shopify store?');
    expect(dialog.textContent).toContain('stops working straight away');

    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    http.expectOne(`${KEYS_API}/1`).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(KEYS_API).flush([key('1', { name: 'Shopify store', revokedAt: '2026-11-15T00:00:00Z' })]);
    await fixture.whenStable();

    expect(dialog.hasAttribute('open')).toBe(false);
    expect(page.querySelector('[role="status"]')?.textContent).toContain('is revoked');
    expect(page.querySelector('tbody .sd-badge')?.textContent).toContain('Revoked');
  });

  it('keeps the dialog open with a message when revoking fails', async () => {
    await open([key('1')]);
    button('Revoke').click();
    await fixture.whenStable();
    page.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectOne(`${KEYS_API}/1`).flush(problem('INTERNAL_ERROR', { status: 500 }), {
      status: 500,
      statusText: 'Server Error',
    });
    await fixture.whenStable();

    const dialog = page.querySelector('dialog')!;
    expect(dialog.hasAttribute('open')).toBe(true);
    expect(dialog.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong');
  });
});
