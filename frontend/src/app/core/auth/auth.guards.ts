import { inject } from '@angular/core';
import { CanActivateFn, RedirectFunction, Router } from '@angular/router';

import { AuthUser, Role } from './auth.models';
import { AuthService, activeRoles } from './auth.service';

/** Fallback when no better page is known, for example after a refused return URL. */
export const HOME_AFTER_LOGIN = '/account';

/**
 * Where each kind of user starts after logging in. Someone with several roles goes to the most
 * specialised one; the navigation shows the others. A user who must change an initial password
 * goes to their account page first, because until then their token carries no roles.
 */
export function homeFor(user: AuthUser | null | undefined): string {
  if (!user) {
    return '/login';
  }
  const roles = activeRoles(user);
  if (roles.includes('DISPATCHER')) {
    return '/dispatcher';
  }
  if (roles.includes('MERCHANT')) {
    return '/merchant';
  }
  if (roles.includes('COURIER')) {
    return '/courier';
  }
  if (roles.includes('CUSTOMER')) {
    return '/customer';
  }
  return HOME_AFTER_LOGIN;
}

/** For `redirectTo` on the root path: each user's own start page. */
export const redirectHome: RedirectFunction = () => homeFor(inject(AuthService).user());

/** Pages for signed-in users. Others go to login and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isSignedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login and register: a signed-in user goes straight to their own start page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isSignedIn() ? inject(Router).parseUrl(homeFor(auth.user())) : true;
};

/**
 * Pages for users holding at least one of the given roles. Signed-out users go to login; signed-in
 * users without the role go to their account page with a notice. This only decides what the app
 * shows: the backend checks the role again on every request.
 */
export function roleGuard(...allowed: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const user = auth.user();
    if (!user) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state?.url } });
    }
    if (activeRoles(user).some((role) => allowed.includes(role))) {
      return true;
    }
    return router.createUrlTree([HOME_AFTER_LOGIN], { queryParams: { notice: 'forbidden' } });
  };
}

/** Dispatcher-only screens. Backend authorization remains the final authority. */
export const dispatcherGuard: CanActivateFn = roleGuard('DISPATCHER');

/** Only same-app paths, so a crafted link can't send people to another site after login. */
export function safeReturnUrl(
  value: string | null | undefined,
  fallback: string = HOME_AFTER_LOGIN,
): string {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : fallback;
}
