import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from './auth.service';
import { exampleUser, tokenResponse } from '../../testing/auth-fixtures';

describe('protected routes', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  function login(user = exampleUser) {
    TestBed.inject(AuthService).login({ email: user.email, password: 'Secret-123' }).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/login')
      .flush(tokenResponse(user));
  }
  it('redirects an anonymous visitor to login', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dispatcher');
    expect(TestBed.inject(Router).url).toContain('/login?returnUrl=');
  });
  it('blocks another role even through a direct URL', async () => {
    login();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dispatcher');
    expect(TestBed.inject(Router).url).toBe('/customer');
  });
  it('redirects a new dispatcher to password change', async () => {
    login({
      ...exampleUser,
      accountRole: 'DISPATCHER',
      roles: ['DISPATCHER'],
      mustChangePassword: true,
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dispatcher');
    expect(TestBed.inject(Router).url).toBe('/change-password');
  });
  it('routes an unassigned Google account to role selection', async () => {
    login({
      ...exampleUser,
      accountRole: undefined,
      roles: [],
      authProviders: ['GOOGLE'],
      onboardingState: 'ROLE_REQUIRED',
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/customer');
    expect(TestBed.inject(Router).url).toBe('/choose-role');
  });
});
