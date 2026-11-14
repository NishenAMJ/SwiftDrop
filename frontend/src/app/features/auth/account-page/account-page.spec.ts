import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthUser } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { exampleUser, problem, tokenResponse } from '../../../testing/auth-fixtures';
import { AccountPage } from './account-page';

describe('AccountPage', () => {
  let http: HttpTestingController;

  async function signedInAs(user: AuthUser = exampleUser) {
    TestBed.configureTestingModule({
      imports: [AccountPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: user.email, password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(user));
    const fixture = TestBed.createComponent(AccountPage);
    await fixture.whenStable();
    return fixture;
  }

  function type(page: HTMLElement, id: string, value: string) {
    const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submitPassword(fixture: Awaited<ReturnType<typeof signedInAs>>) {
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  it('shows who is signed in and their roles as text', async () => {
    const page = (await signedInAs({ ...exampleUser, roles: ['CUSTOMER', 'MERCHANT'] })).nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Hello, Nimal Perera');
    expect(page.textContent).toContain('nimal@example.com');
    expect([...page.querySelectorAll('.sd-badge')].map((b) => b.textContent?.trim())).toEqual([
      'Customer',
      'Merchant',
    ]);
  });

  it('offers customers the merchant and courier applications', async () => {
    const page = (await signedInAs()).nativeElement as HTMLElement;
    expect(page.querySelector('a[href="/merchant/application"]')?.textContent).toContain(
      'Apply to become a merchant',
    );
    expect(page.querySelector('a[href="/courier/application"]')).not.toBeNull();
  });

  it('lets a new dispatcher replace the initial password, then opens the dispatcher tools', async () => {
    const newDispatcher: AuthUser = { ...exampleUser, roles: ['DISPATCHER'], mustChangePassword: true };
    const fixture = await signedInAs(newDispatcher);
    const page = fixture.nativeElement as HTMLElement;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    expect(page.querySelector('.sd-alert--warning')?.textContent).toContain('initial password');
    // No customer links for a dispatcher.
    expect(page.querySelector('a[href="/merchant/application"]')).toBeNull();

    type(page, 'account-currentPassword', 'Initial-pass-1');
    type(page, 'account-newPassword', 'My-own-pass-2');
    type(page, 'account-confirmPassword', 'My-own-pass-2');
    await submitPassword(fixture);

    const change = http.expectOne('/api/v1/users/me/password');
    expect(change.request.body).toEqual({ currentPassword: 'Initial-pass-1', newPassword: 'My-own-pass-2' });
    change.flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush(tokenResponse({ ...newDispatcher, mustChangePassword: false }));

    expect(navigate).toHaveBeenCalledWith('/dispatcher');
  });

  it('marks a wrong current password on its field', async () => {
    const fixture = await signedInAs();
    const page = fixture.nativeElement as HTMLElement;
    type(page, 'account-currentPassword', 'Wrong-pass-1');
    type(page, 'account-newPassword', 'My-own-pass-2');
    type(page, 'account-confirmPassword', 'My-own-pass-2');
    await submitPassword(fixture);

    http
      .expectOne('/api/v1/users/me/password')
      .flush(problem('INVALID_CREDENTIALS', { status: 403 }), { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    const current = page.querySelector('#account-currentPassword')!;
    expect(current.getAttribute('aria-invalid')).toBe('true');
    expect(page.querySelector('#account-currentPassword-error')?.textContent).toContain('incorrect');
  });

  it("doesn't send mismatched passwords", async () => {
    const fixture = await signedInAs();
    const page = fixture.nativeElement as HTMLElement;
    type(page, 'account-currentPassword', 'Old-pass-1');
    type(page, 'account-newPassword', 'My-own-pass-2');
    type(page, 'account-confirmPassword', 'Different-3');
    await submitPassword(fixture);

    http.expectNone('/api/v1/users/me/password');
    expect(page.querySelector('#account-confirmPassword-error')?.textContent).toContain("don't match");
  });

  it('lets a Google-only account set a first password without a current one', async () => {
    const fixture = await signedInAs({ ...exampleUser, authProviders: ['GOOGLE'] });
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('#account-currentPassword')).toBeNull();
    expect(page.textContent).toContain('Set a password');

    type(page, 'account-newPassword', 'My-own-pass-2');
    type(page, 'account-confirmPassword', 'My-own-pass-2');
    await submitPassword(fixture);

    const change = http.expectOne('/api/v1/users/me/password');
    expect(change.request.body).toEqual({ currentPassword: undefined, newPassword: 'My-own-pass-2' });
    change.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse({ ...exampleUser, authProviders: ['GOOGLE', 'PASSWORD'] }));
    await fixture.whenStable();

    expect(page.querySelector('.sd-alert--success')?.textContent).toContain('Password changed');
  });
});
