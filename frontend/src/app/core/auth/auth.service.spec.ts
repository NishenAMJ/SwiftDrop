import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { exampleUser, tokenResponse } from '../../testing/auth-fixtures';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the access token and user after login', async () => {
    const result = firstValueFrom(auth.login({ email: 'nimal@example.com', password: 'Secret-pass-1' }));
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'nimal@example.com', password: 'Secret-pass-1' });
    request.flush(tokenResponse());

    expect(await result).toEqual(exampleUser);
    expect(auth.token()).toBe('access-1');
    expect(auth.isSignedIn()).toBe(true);
  });

  it('shares one refresh request between callers', async () => {
    const first = firstValueFrom(auth.refresh());
    const second = firstValueFrom(auth.refresh());
    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse(exampleUser, 'access-2'));

    expect(await first).toEqual(exampleUser);
    expect(await second).toEqual(exampleUser);
    expect(auth.token()).toBe('access-2');
  });

  it('restores nothing, quietly, when there is no valid refresh cookie', async () => {
    const restored = auth.restoreSession();
    http.expectOne('/api/v1/auth/refresh').flush({ code: 'REFRESH_TOKEN_INVALID' }, { status: 401, statusText: 'Unauthorized' });
    await restored;

    expect(auth.isSignedIn()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('forgets the session on logout', async () => {
    auth.register({ email: 'a@example.com', password: 'Secret-pass-1', fullName: 'A' }).subscribe();
    http.expectOne('/api/v1/auth/register').flush(tokenResponse());

    const done = firstValueFrom(auth.logout(), { defaultValue: undefined });
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await done;

    expect(auth.isSignedIn()).toBe(false);
    expect(auth.token()).toBeNull();
  });
});
