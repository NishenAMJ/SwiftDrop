import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AUTH_API, AuthService } from './auth.service';

/**
 * Adds the access token to API calls. If a call fails with 401 (token expired), refreshes once
 * and retries; if that fails too, the session is cleared. Auth endpoints are left alone, since
 * they work with the refresh cookie.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/') || request.url.startsWith(`${AUTH_API}/`)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const token = auth.token();

  return next(withToken(request, token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !token) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError(() => {
          auth.clear();
          return throwError(() => error);
        }),
        switchMap(() => next(withToken(request, auth.token()))),
      );
    }),
  );
};

function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}
