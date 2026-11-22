import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, RedirectFunction, ActivatedRouteSnapshot, UrlSegment } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { COURIER_ROUTES, courierHome } from './courier.routes';

describe('courier routes', () => {
  function startPage(roles: string[] | null): string {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { hasRole: (role: string) => roles?.includes(role) ?? false },
        },
      ],
    });
    const redirect = COURIER_ROUTES.find((route) => route.path === '' && route.redirectTo)!.redirectTo;
    expect(redirect).toBe(courierHome);
    return runInInjectionContext(TestBed.inject(Injector), () =>
      (redirect as RedirectFunction)({} as ActivatedRouteSnapshot & { redirectTo: string }) as string,
    );
  }

  it('opens the deliveries for an approved courier', () => {
    expect(startPage(['COURIER'])).toBe('deliveries');
  });

  it('opens the application for a customer who is applying to become a courier', () => {
    expect(startPage(['CUSTOMER'])).toBe('application');
  });

  it('opens the application for someone with no roles yet', () => {
    expect(startPage([])).toBe('application');
  });

  it('is the default only for the bare /courier address', () => {
    const root = COURIER_ROUTES.find((route) => route.path === '') as Route;
    expect(root.pathMatch).toBe('full');
  });

  it('keeps both pages reachable by their own address, each behind its own role', () => {
    const byPath = (path: string) => COURIER_ROUTES.find((route) => route.path === path) as Route;

    expect(byPath('deliveries').canActivate).toHaveLength(1);
    expect(byPath('application').canActivate).toHaveLength(1);
    expect(byPath('deliveries').redirectTo).toBeUndefined();
    expect(byPath('application').redirectTo).toBeUndefined();
  });
});
