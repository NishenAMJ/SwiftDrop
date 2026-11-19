import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { exampleUser, problem, tokenResponse } from '../../../testing/auth-fixtures';
import { Merchant } from '../merchant.models';
import { MerchantApplicationPage } from './merchant-application-page';

const merchant: Merchant = {
  id: 'c71a4e2b-9d3f-4b8a-b6e1-2f5c8d0a7e64',
  userId: exampleUser.id,
  businessName: 'Lanka Crafts',
  businessType: 'COMPANY',
  businessRegistrationNumber: 'PV 12345',
  contactPhone: '+94112345678',
  businessAddress: { line1: '12 Galle Road', city: 'Colombo', postalCode: '00300' },
  status: 'PENDING',
  submittedAt: '2026-11-10T04:30:00Z',
  createdAt: '2026-11-10T04:30:00Z',
};

describe('MerchantApplicationPage', () => {
  let fixture: ComponentFixture<MerchantApplicationPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MerchantApplicationPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(me: Merchant | 'none') {
    fixture = TestBed.createComponent(MerchantApplicationPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    const request = http.expectOne('/api/v1/merchants/me');
    if (me === 'none') {
      request.flush(problem('NOT_FOUND', { status: 404 }), { status: 404, statusText: 'Not Found' });
    } else {
      request.flush(me);
    }
    await fixture.whenStable();
  }

  function type(id: string, value: string) {
    const input = page.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  async function submit() {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('shows the application form to a customer who has never applied', async () => {
    await open('none');
    expect(page.querySelector('h1')?.textContent).toContain('Become a merchant');
    expect(page.querySelector('label[for="merchant-businessName"]')).not.toBeNull();
    expect(page.querySelector('fieldset legend')?.textContent).toContain('Business address');
  });

  it('explains missing fields on submit without calling the server', async () => {
    await open('none');
    await submit();

    http.expectNone('/api/v1/merchants');
    const name = page.querySelector('#merchant-businessName')!;
    expect(name.getAttribute('aria-invalid')).toBe('true');
    expect(name.getAttribute('aria-describedby')).toBe('merchant-businessName-error');
    expect(page.querySelector('#merchant-businessName-error')?.textContent).toContain('business name');
    expect(document.activeElement).toBe(name);
  });

  it('requires a registration number for companies only', async () => {
    await open('none');
    type('merchant-businessType', 'COMPANY');
    await submit();
    expect(page.querySelector('#merchant-businessRegistrationNumber-error')?.textContent).toContain(
      'registration number',
    );

    type('merchant-businessType', 'INDIVIDUAL');
    await fixture.whenStable();
    expect(page.querySelector('#merchant-businessRegistrationNumber-error')).toBeNull();
  });

  it('sends a trimmed application without empty optional fields, then shows it as pending', async () => {
    await open('none');
    type('merchant-businessName', ' Lanka Crafts ');
    type('merchant-businessType', 'INDIVIDUAL');
    type('merchant-contactPhone', '+94112345678');
    type('merchant-line1', '12 Galle Road');
    type('merchant-city', 'Colombo');
    await submit();

    const request = http.expectOne('/api/v1/merchants');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      businessName: 'Lanka Crafts',
      businessType: 'INDIVIDUAL',
      businessRegistrationNumber: undefined,
      contactEmail: undefined,
      contactPhone: '+94112345678',
      businessAddress: { line1: '12 Galle Road', line2: undefined, city: 'Colombo', postalCode: undefined },
    });
    request.flush({ ...merchant, businessType: 'INDIVIDUAL' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(page.querySelector('[role="status"]')?.textContent).toContain('Application sent');
    expect(page.querySelector('.sd-badge')?.textContent).toContain('Pending approval');
    expect(page.querySelector('form')).toBeNull();
  });

  it('shows the rejection reason and lets the applicant resubmit the same details', async () => {
    await open({
      ...merchant,
      status: 'REJECTED',
      lastDecision: { status: 'REJECTED', reason: 'Registration number not found.', decidedAt: '2026-11-11T05:00:00Z' },
    });

    expect(page.querySelector('.sd-badge')?.textContent).toContain('Rejected');
    expect(page.textContent).toContain('Registration number not found.');
    expect(page.querySelector<HTMLInputElement>('#merchant-businessName')!.value).toBe('Lanka Crafts');
    expect(page.querySelector('button[type="submit"]')?.textContent).toContain('Send application again');
  });

  it('refreshes the session when the application was approved, so merchant access arrives', async () => {
    TestBed.inject(AuthService).login({ email: exampleUser.email, password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse());

    await open({ ...merchant, status: 'APPROVED' });

    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse({ ...exampleUser, roles: ['CUSTOMER', 'MERCHANT'] }));
    await fixture.whenStable();
    expect(page.querySelector('h1')?.textContent).toContain('Merchant account');
    expect(page.querySelector('a[href="/merchant/api-keys"]')).not.toBeNull();
  });

  it('offers to try again when the application cannot be loaded', async () => {
    fixture = TestBed.createComponent(MerchantApplicationPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    http.expectOne('/api/v1/merchants/me').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("couldn't load");
    page.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    http.expectOne('/api/v1/merchants/me').flush(merchant);
  });
});
