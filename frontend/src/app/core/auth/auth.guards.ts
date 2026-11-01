import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { canVisit, destinationFor, homeFor } from './auth-routing';

export const HOME_AFTER_LOGIN = '/account';
export const authGuard: CanActivateFn = (_route, state) => {
  const user = inject(AuthService).user();
  const router = inject(Router);
  if (!user) return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  return canVisit(user, state.url) || router.parseUrl(homeFor(user));
};
export const guestGuard: CanActivateFn = () => {
  const user = inject(AuthService).user();
  return user ? inject(Router).parseUrl(homeFor(user)) : true;
};
export const homeGuard: CanActivateFn = () =>
  inject(Router).parseUrl(homeFor(inject(AuthService).user()));
export function safeReturnUrl(value: string | null | undefined): string {
  return value && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\')
    ? value
    : HOME_AFTER_LOGIN;
}
export { destinationFor, homeFor };
