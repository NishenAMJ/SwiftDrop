import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { problem } from '../../../testing/auth-fixtures';
import { WebhookSubscription } from '../webhook.models';
import { WebhooksPage } from './webhooks-page';

const API = '/api/v1/webhooks/subscriptions';

function sub(id: string, extra: Partial<WebhookSubscription> = {}): WebhookSubscription {
  return {
    id,
    merchantId: 'm-1',
    url: `https://shop.example.com/hook-${id}`,
    eventTypes: ['parcel.status-changed'],
    active: true,
    createdAt: '2026-11-01T04:30:00Z',
    updatedAt: '2026-11-01T04:30:00Z',
    ...extra,
  };
}

function pageOf(items: WebhookSubscription[]) {
  return { content: items, page: 0, size: 50, totalElements: items.length, totalPages: 1 };
}

describe('WebhooksPage', () => {
  let fixture: ComponentFixture<WebhooksPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  async function open(items: WebhookSubscription[]) {
    TestBed.configureTestingModule({
      imports: [WebhooksPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WebhooksPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    http.expectOne((req) => req.url === API && req.method === 'GET').flush(pageOf(items));
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

  function submit() {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  it('lists webhooks with their status in words', async () => {
    await open([sub('1'), sub('2', { active: false, pausedReason: 'Paused by merchant' })]);

    const cards = [...page.querySelectorAll('.webhook')];
    expect(cards.length).toBe(2);
    expect(cards[0].querySelector('.sd-badge')?.textContent?.trim()).toBe('Active');
    expect(cards[1].querySelector('.sd-badge')?.textContent?.trim()).toBe('Paused');
    expect(cards[0].textContent).toContain('Parcel status changes');
    expect(cards[1].textContent).toContain('Resume');
  });

  it('explains when there are no webhooks yet', async () => {
    await open([]);
    expect(page.textContent).toContain("You haven't added a webhook yet");
  });

  it('asks for a full address before sending anything', async () => {
    await open([]);
    type('webhook-url', 'not a url');
    submit();
    await fixture.whenStable();

    expect(page.textContent).toContain('Enter a full address that starts with https://');
    http.expectNone((req) => req.method === 'POST');
  });

  it('shows the signing secret once, and forgets it when the merchant has saved it', async () => {
    await open([]);
    type('webhook-url', ' https://shop.example.com/hook ');
    submit();
    await fixture.whenStable();

    const request = http.expectOne((req) => req.url === API && req.method === 'POST');
    expect(request.request.body).toEqual({
      url: 'https://shop.example.com/hook',
      eventTypes: ['parcel.status-changed'],
    });
    request.flush({ ...sub('9', { url: 'https://shop.example.com/hook' }), secret: 'whsec_abc123secret' });
    await fixture.whenStable();

    const shown = page.querySelector<HTMLInputElement>('#new-secret-value')!;
    expect(shown.value).toBe('whsec_abc123secret');
    expect(shown.readOnly).toBe(true);
    expect(page.textContent).toContain('only time the secret is shown');
    // The list never holds the secret.
    expect(page.querySelector('.webhook-list')?.textContent).not.toContain('whsec_');

    button("I've saved the secret").click();
    await fixture.whenStable();
    expect(page.querySelector('#new-secret-value')).toBeNull();
    expect(page.textContent).not.toContain('whsec_abc123secret');
  });

  it('puts the server\'s URL message on the field', async () => {
    await open([]);
    type('webhook-url', 'https://10.0.0.5/hook');
    submit();
    await fixture.whenStable();

    http
      .expectOne((req) => req.method === 'POST')
      .flush(
        problem('VALIDATION_FAILED', {
          errors: [{ field: 'url', message: 'Private, loopback and link-local addresses are not allowed.' }],
        }),
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(page.querySelector('#webhook-url-error')?.textContent).toContain('Private, loopback');
  });

  it('pauses a webhook', async () => {
    await open([sub('1')]);
    button('Pause').click();
    await fixture.whenStable();

    const request = http.expectOne(`${API}/1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ active: false });
    request.flush(sub('1', { active: false }));
    await fixture.whenStable();

    expect(page.querySelector('.webhook .sd-badge')?.textContent?.trim()).toBe('Paused');
    expect(page.querySelector('[role="status"]')?.textContent).toContain('Webhook paused');
  });

  it('queues a test message and links to the deliveries', async () => {
    await open([sub('1')]);
    button('Send test').click();
    await fixture.whenStable();

    const request = http.expectOne(`${API}/1/test`);
    expect(request.request.method).toBe('POST');
    request.flush({ id: 'd-1', status: 'PENDING' }, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();

    expect(page.textContent).toContain('Test message queued');
  });

  it('deletes a webhook after confirming what will happen', async () => {
    await open([sub('1')]);
    button('Delete').click();
    await fixture.whenStable();

    const dialog = page.querySelector('dialog')!;
    expect(dialog.hasAttribute('open')).toBe(true);
    expect(dialog.textContent).toContain("This can't be undone");

    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    const request = http.expectOne(`${API}/1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(dialog.hasAttribute('open')).toBe(false);
    expect(page.querySelectorAll('.webhook').length).toBe(0);
    expect(page.textContent).toContain('Webhook deleted');
  });

  it('shows the new secret after rotating it', async () => {
    await open([sub('1')]);
    button('New secret').click();
    await fixture.whenStable();
    page.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectOne(`${API}/1/rotate-secret`).flush({ ...sub('1'), secret: 'whsec_rotated' });
    await fixture.whenStable();

    expect(page.querySelector<HTMLInputElement>('#new-secret-value')?.value).toBe('whsec_rotated');
  });

  it('explains when the merchant is not approved yet', async () => {
    TestBed.configureTestingModule({
      imports: [WebhooksPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WebhooksPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    http
      .expectOne(API + '?page=0&size=50')
      .flush(problem('MERCHANT_NOT_APPROVED', { status: 403 }), { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('once your merchant account is approved');
  });
});
