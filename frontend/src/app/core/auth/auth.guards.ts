import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const HOME_AFTER_LOGIN = '/account';

/** Pages for signed-in users. Others go to login and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isSignedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login and register: a signed-in user goes straight to their account. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isSignedIn() ? inject(Router).parseUrl(HOME_AFTER_LOGIN) : true;

/** Only same-app paths, so a crafted link can't send people to another site after login. */
export function safeReturnUrl(value: string | null | undefined): string {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : HOME_AFTER_LOGIN;
}
