import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, UrlTree } from '@angular/router';

import { exampleUser } from '../../testing/auth-fixtures';
import { AuthUser } from './auth.models';
import { AuthService } from './auth.service';
import { dispatcherGuard, HOME_AFTER_LOGIN, homeFor, roleGuard, safeReturnUrl } from './auth.guards';

describe('safeReturnUrl', () => {
  it('keeps paths inside the app', () => {
    expect(safeReturnUrl('/account')).toBe('/account');
    expect(safeReturnUrl('/merchant/orders?page=2')).toBe('/merchant/orders?page=2');
  });

  it('refuses anything that could leave the app', () => {
    for (const value of [
      'https://evil.example',
      '//evil.example',
      'evil.example',
      '',
      null,
      undefined,
    ]) {
      expect(safeReturnUrl(value)).toBe(HOME_AFTER_LOGIN);
    }
  });

  it('falls back to the given page', () => {
    expect(safeReturnUrl(null, '/dispatcher')).toBe('/dispatcher');
  });
});

describe('homeFor', () => {
  const as = (roles: AuthUser['roles'], extra: Partial<AuthUser> = {}): AuthUser => ({
    ...exampleUser,
    roles,
    ...extra,
  });

  it('sends each role to its own start page, most specialised first', () => {
    expect(homeFor(as(['CUSTOMER']))).toBe('/customer');
    expect(homeFor(as(['CUSTOMER', 'COURIER']))).toBe('/courier');
    expect(homeFor(as(['CUSTOMER', 'MERCHANT']))).toBe('/merchant');
    expect(homeFor(as(['CUSTOMER', 'MERCHANT', 'COURIER']))).toBe('/merchant');
    expect(homeFor(as(['DISPATCHER']))).toBe('/dispatcher');
  });

  it('sends a dispatcher with an initial password to the account page first', () => {
    expect(homeFor(as(['DISPATCHER'], { mustChangePassword: true }))).toBe('/account');
  });

  it('sends signed-out visitors to login', () => {
    expect(homeFor(null)).toBe('/login');
  });
});

describe('roleGuard', () => {
  function run(user: Partial<AuthUser> | null, guard = roleGuard('MERCHANT')) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { user: signal(user) } }],
    });
    const result = TestBed.runInInjectionContext(() =>
      guard({} as never, { url: '/merchant/api-keys' } as never),
    );
    return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;
  }

  it('lets in users with one of the roles', () => {
    expect(run({ roles: ['CUSTOMER', 'MERCHANT'] })).toBe(true);
    expect(run({ roles: ['COURIER'] }, roleGuard('MERCHANT', 'COURIER'))).toBe(true);
  });

  it('sends signed-out visitors to login and back afterwards', () => {
    expect(run(null)).toBe('/login?returnUrl=%2Fmerchant%2Fapi-keys');
  });

  it('sends users without the role to their account with a notice', () => {
    expect(run({ roles: ['CUSTOMER'] })).toBe('/account?notice=forbidden');
  });

  it('ignores roles the token does not carry yet (initial password)', () => {
    expect(run({ roles: ['DISPATCHER'], mustChangePassword: true }, dispatcherGuard)).toBe(
      '/account?notice=forbidden',
    );
  });
});

describe('dispatcherGuard', () => {
  it('allows dispatchers and redirects other signed-in roles', () => {
    const user = signal({ roles: ['DISPATCHER'] });
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { user } }],
    });

    const allowed = TestBed.runInInjectionContext(() => dispatcherGuard({} as never, {} as never));
    expect(allowed).toBe(true);

    user.set({ roles: ['CUSTOMER'] });
    const denied = TestBed.runInInjectionContext(() => dispatcherGuard({} as never, {} as never));
    expect(denied).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(denied as UrlTree)).toBe(
      '/account?notice=forbidden',
    );
  });
});
