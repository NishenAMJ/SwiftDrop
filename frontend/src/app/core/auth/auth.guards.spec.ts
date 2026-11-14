import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, UrlTree } from '@angular/router';

import { AuthService } from './auth.service';
import { dispatcherGuard, HOME_AFTER_LOGIN, safeReturnUrl } from './auth.guards';

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
