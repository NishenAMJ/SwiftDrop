import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, firstValueFrom, map, shareReplay, tap } from 'rxjs';

import { AuthUser, LoginRequest, RegisterRequest, TokenResponse } from './auth.models';

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
