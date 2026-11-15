import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, firstValueFrom, map, shareReplay, switchMap, tap } from 'rxjs';

import {
  AuthUser,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  Role,
  TokenResponse,
} from './auth.models';

export const AUTH_API = '/api/v1/auth';

/** Starting point of Google login: a full-page navigation, not an API call. */
export const GOOGLE_LOGIN_URL = '/oauth2/authorization/google';

/**
 * The signed-in session. The access token is kept in memory only (never localStorage); the
 * refresh token is an HttpOnly cookie that JavaScript never sees. See
 * docs/architecture/authentication.md.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private accessToken: string | null = null;
  private readonly currentUser = signal<AuthUser | null>(null);
  private refreshInFlight: Observable<AuthUser> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isSignedIn = computed(() => this.currentUser() !== null);

  /**
   * Roles the access token actually carries. Same as `user.roles`, except while the user must
   * change an initial password: then the token has none, so no role-specific screen would work.
   */
  readonly roles = computed<readonly Role[]>(() => activeRoles(this.currentUser()));

  hasRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  token(): string | null {
    return this.accessToken;
  }

  login(request: LoginRequest): Observable<AuthUser> {
    return this.startSession(this.http.post<TokenResponse>(`${AUTH_API}/login`, request));
  }

  register(request: RegisterRequest): Observable<AuthUser> {
    return this.startSession(this.http.post<TokenResponse>(`${AUTH_API}/register`, request));
  }

  /**
   * New access token from the refresh cookie. Concurrent callers share one request, so several
   * failing API calls trigger a single refresh.
   */
  refresh(): Observable<AuthUser> {
    this.refreshInFlight ??= this.startSession(
      this.http.post<TokenResponse>(`${AUTH_API}/refresh`, null),
    ).pipe(
      finalize(() => (this.refreshInFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshInFlight;
  }

  /** At app start: sign back in from the refresh cookie if there is a valid one. */
  async restoreSession(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.clear();
    }
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${AUTH_API}/logout`, null).pipe(finalize(() => this.clear()));
  }

  clear(): void {
    this.accessToken = null;
    this.currentUser.set(null);
  }

  /**
   * Changes (or, for a Google-only account, sets) the password. Identity revokes every other
   * session and sets a new refresh cookie; one refresh then returns a token with the user's roles,
   * which matters for a new dispatcher whose token had none.
   */
  changePassword(request: ChangePasswordRequest): Observable<AuthUser> {
    return this.http
      .post<void>('/api/v1/users/me/password', request)
      .pipe(switchMap(() => this.refresh()));
  }

  private startSession(request: Observable<TokenResponse>): Observable<AuthUser> {
    return request.pipe(
      tap((response) => {
        this.accessToken = response.accessToken;
        this.currentUser.set(response.user ?? null);
      }),
      map((response) => response.user as AuthUser),
    );
  }
}

export function activeRoles(user: AuthUser | null | undefined): readonly Role[] {
  if (!user || user.mustChangePassword) {
    return [];
  }
  return user.roles;
}
