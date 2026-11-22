import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { WebhookService } from '../webhook.service';
import { FailedWebhooksPage } from './failed-webhooks-page';

describe('FailedWebhooksPage', () => {
  function setup(result: () => Observable<unknown>) {
    const api = { failed: vi.fn(result) };
    TestBed.configureTestingModule({ imports: [FailedWebhooksPage], providers: [{ provide: WebhookService, useValue: api }] });
    const fixture = TestBed.createComponent(FailedWebhooksPage);
    fixture.detectChanges();
    return { page: fixture.componentInstance, api, text: () => fixture.nativeElement.textContent as string };
  }

  it('loads the first page of failed deliveries', () => {
    const { page, api } = setup(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    expect(api.failed).toHaveBeenCalledWith(0, 20);
    expect(page['loading']()).toBe(false);
    expect(page['loadError']()).toBeNull();
  });

  it('shows a connection message instead of raw errors', () => {
    const { page, text } = setup(() => throwError(() => new HttpErrorResponse({ status: 0, statusText: 'ECONNREFUSED' })));
    expect(page['loadError']()).toContain("Can't reach SwiftDrop");
    expect(text()).not.toContain('ECONNREFUSED');
  });

  it('reloads the current page', () => {
    const { page, api } = setup(() => of({ content: [], page: 2, size: 20, totalElements: 41, totalPages: 3 }));
    page['load']();
    expect(api.failed).toHaveBeenLastCalledWith(2, 20);
  });
});
