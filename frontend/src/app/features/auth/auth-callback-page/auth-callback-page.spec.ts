import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { tokenResponse } from '../../../testing/auth-fixtures';
import { AuthCallbackPage } from './auth-callback-page';

describe('AuthCallbackPage', () => {
  let http: HttpTestingController;

  function create(query: Record<string, string>) {
    TestBed.configureTestingModule({
      imports: [AuthCallbackPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(AuthCallbackPage);
  }

  afterEach(() => http.verify());

  it('finishes Google login with one refresh and goes to the account page', async () => {
    const fixture = create({});
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Finishing Google login',
    );

    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse());
    expect(navigate).toHaveBeenCalledWith('/customer', { replaceUrl: true });
  });

  it('explains errors sent back by identity without calling it', async () => {
    const fixture = create({ error: 'email_not_verified' });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      "Google hasn't confirmed this email address",
    );
    expect(fixture.nativeElement.querySelector('a[href="/login"]')).not.toBeNull();
    http.expectNone('/api/v1/auth/refresh');
  });

  it('uses a general message for unknown error values', async () => {
    const fixture = create({ error: '<script>' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      "Google login didn't finish",
    );
  });
});
