import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { OnboardingPage } from './onboarding-page';
import { AuthService } from '../../../core/auth/auth.service';
import { exampleUser, tokenResponse } from '../../../testing/auth-fixtures';
import { AuthUser } from '../../../core/auth/auth.models';

describe('application onboarding', () => {
  async function setup(user: AuthUser) {
    TestBed.configureTestingModule({
      imports: [OnboardingPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: user.email, password: 'Secret-123' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(user));
    const fixture = TestBed.createComponent(OnboardingPage);
    await fixture.whenStable();
    return { http, fixture, page: fixture.nativeElement as HTMLElement };
  }
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('submits a bicycle application and shows pending status after refresh', async () => {
    const user: AuthUser = {
      ...exampleUser,
      accountRole: 'COURIER',
      roles: [],
      onboardingState: 'APPLICATION_REQUIRED',
    };
    const { http, fixture, page } = await setup(user);
    const field = page.querySelector<HTMLInputElement>('#nationalIdNumber')!;
    field.value = '200012345678';
    field.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/v1/couriers');
    expect(request.request.body.vehicleType).toBe('BICYCLE');
    request.flush({ status: 'PENDING' });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush(tokenResponse({ ...user, onboardingState: 'PENDING' }));
    http
      .expectOne('/api/v1/couriers/me')
      .flush({ status: 'PENDING', vehicleType: 'BICYCLE', nationalIdNumber: '200012345678' });
    await fixture.whenStable();
    expect(page.querySelector('form')).toBeNull();
    expect(page.textContent).toContain('Pending approval');
  });
  it('shows a rejection reason and allows resubmission', async () => {
    const { http, fixture, page } = await setup({
      ...exampleUser,
      accountRole: 'MERCHANT',
      roles: [],
      onboardingState: 'REJECTED',
    });
    http
      .expectOne('/api/v1/merchants/me')
      .flush({
        businessName: 'Test shop',
        businessType: 'COMPANY',
        lastDecision: { reason: 'Registration number missing' },
      });
    await fixture.whenStable();
    expect(page.textContent).toContain('Registration number missing');
    expect(page.querySelector('form')).not.toBeNull();
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectNone('/api/v1/merchants');
  });
  it('keeps a suspended account restricted and enters the dashboard only after approval refresh', async () => {
    const user: AuthUser = {
      ...exampleUser,
      accountRole: 'MERCHANT',
      roles: [],
      onboardingState: 'SUSPENDED',
    };
    const { http, fixture, page } = await setup(user);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http.expectOne('/api/v1/merchants/me').flush({ lastDecision: { reason: 'Review required' } });
    await fixture.whenStable();
    expect(page.querySelector('form')).toBeNull();
    [...page.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Refresh status'))!
      .click();
    http
      .expectOne('/api/v1/auth/refresh')
      .flush(tokenResponse({ ...user, onboardingState: 'READY', roles: ['MERCHANT'] }));
    expect(navigate).toHaveBeenCalledWith('/merchant');
  });
});
