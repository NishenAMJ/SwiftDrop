import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { tokenResponse } from '../../testing/auth-fixtures';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    auth.login({ email: 'a@example.com', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(undefined, 'old-token'));
  });

  afterEach(() => http.verify());

  it('adds the access token to API calls but not to auth calls', () => {
    client.get('/api/v1/users/me').subscribe();
    expect(http.expectOne('/api/v1/users/me').request.headers.get('Authorization')).toBe('Bearer old-token');

    client.post('/api/v1/auth/logout', null).subscribe();
    expect(http.expectOne('/api/v1/auth/logout').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    const result = firstValueFrom(client.get<{ ok: boolean }>('/api/v1/users/me'));
    http.expectOne('/api/v1/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse(undefined, 'new-token'));

    const retry = http.expectOne('/api/v1/users/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    retry.flush({ ok: true });
    expect(await result).toEqual({ ok: true });
  });

  it('signs out when the refresh fails too', async () => {
    const result = firstValueFrom(client.get('/api/v1/users/me'));
    http.expectOne('/api/v1/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(auth.isSignedIn()).toBe(false);
  });
});
