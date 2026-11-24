import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { problem } from '../../../testing/auth-fixtures';
import { WebhookDelivery } from '../webhook.models';
import { WebhookDeliveriesPage } from './webhook-deliveries-page';

const SUB_API = '/api/v1/webhooks/subscriptions/s-1';

function delivery(id: string, extra: Partial<WebhookDelivery> = {}): WebhookDelivery {
  return {
    id,
    subscriptionId: 's-1',
    eventId: `e-${id}`,
    eventType: 'parcel.status-changed',
    status: 'DELIVERED',
    attemptCount: 1,
    lastStatusCode: 200,
    createdAt: '2026-11-01T04:30:00Z',
    deliveredAt: '2026-11-01T04:30:05Z',
    ...extra,
  };
}

function pageOf(items: WebhookDelivery[]) {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

describe('WebhookDeliveriesPage', () => {
  let fixture: ComponentFixture<WebhookDeliveriesPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  async function open(items: WebhookDelivery[]) {
    TestBed.configureTestingModule({
      imports: [WebhookDeliveriesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 's-1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WebhookDeliveriesPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    http.expectOne(SUB_API).flush({
      id: 's-1',
      merchantId: 'm-1',
      url: 'https://shop.example.com/hook',
      eventTypes: ['parcel.status-changed'],
      active: true,
      createdAt: '2026-11-01T04:30:00Z',
      updatedAt: '2026-11-01T04:30:00Z',
    });
    http.expectOne((req) => req.url === `${SUB_API}/deliveries`).flush(pageOf(items));
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  it('lists deliveries with status in words and the result of the last attempt', async () => {
    await open([
      delivery('1'),
      delivery('2', { status: 'FAILED', attemptCount: 6, lastStatusCode: 503, deliveredAt: undefined }),
    ]);

    const rows = [...page.querySelectorAll('tbody tr')];
    expect(rows.map((row) => row.querySelector('.sd-badge')?.textContent?.trim())).toEqual([
      'Delivered',
      'Failed',
    ]);
    expect(rows[1].textContent).toContain('Your server answered HTTP 503');
    expect(rows[1].textContent).toContain('No more tries');
    // Only failed deliveries can be sent again.
    expect(rows[0].querySelector('button')).toBeNull();
    expect(rows[1].querySelector('button')?.textContent).toContain('Send again');
    expect(page.textContent).toContain('https://shop.example.com/hook');
  });

  it('explains when nothing has been sent yet', async () => {
    await open([]);
    expect(page.textContent).toContain('Nothing has been sent yet');
  });

  it('sends a failed delivery again and refreshes the list', async () => {
    await open([delivery('2', { status: 'FAILED', attemptCount: 6, lastError: 'timeout', lastStatusCode: undefined })]);
    expect(page.textContent).toContain('Could not reach your server');

    page.querySelector<HTMLButtonElement>('tbody button')!.click();
    await fixture.whenStable();

    const request = http.expectOne('/api/v1/webhooks/deliveries/2/retry');
    expect(request.request.method).toBe('POST');
    request.flush(delivery('2', { status: 'PENDING', attemptCount: 0 }), { status: 202, statusText: 'Accepted' });
    http
      .expectOne((req) => req.url === `${SUB_API}/deliveries`)
      .flush(pageOf([delivery('2', { status: 'PENDING', attemptCount: 0, lastStatusCode: undefined })]));
    await fixture.whenStable();

    expect(page.querySelector('[role="status"]')?.textContent).toContain('Sending again');
    expect(page.querySelector('tbody .sd-badge')?.textContent?.trim()).toBe('Sending');
  });

  it("says so when the webhook doesn't exist or isn't the merchant's", async () => {
    TestBed.configureTestingModule({
      imports: [WebhookDeliveriesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 's-1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WebhookDeliveriesPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    const notFound = { status: 404, statusText: 'Not Found' };
    http.expectOne(SUB_API).flush(problem('SUBSCRIPTION_NOT_FOUND', { status: 404 }), notFound);
    http
      .expectOne((req) => req.url === `${SUB_API}/deliveries`)
      .flush(problem('SUBSCRIPTION_NOT_FOUND', { status: 404 }), notFound);
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("isn't yours");
    expect(page.querySelector('table')).toBeNull();
  });
});
