import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { exampleUser, tokenResponse } from '../../../testing/auth-fixtures';
import { AccountPage } from './account-page';

describe('AccountPage', () => {
  let http: HttpTestingController;

  async function signedInAs(user = exampleUser) {
    TestBed.configureTestingModule({
      imports: [AccountPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: user.email, password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(user));
    const fixture = TestBed.createComponent(AccountPage);
    await fixture.whenStable();
    return fixture;
  }

  afterEach(() => http.verify());

  it('shows who is signed in and their roles as text', async () => {
    const page = (
      await signedInAs({ ...exampleUser, accountRole: 'MERCHANT', roles: ['MERCHANT'] })
    ).nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Hello, Nimal Perera');
    expect(page.textContent).toContain('nimal@example.com');
    expect([...page.querySelectorAll('.sd-badge')].map((b) => b.textContent?.trim())).toEqual([
      'Merchant',
    ]);
  });

  it('tells a new dispatcher to change the initial password', async () => {
    const page = (
      await signedInAs({ ...exampleUser, roles: ['DISPATCHER'], mustChangePassword: true })
    ).nativeElement as HTMLElement;
    expect(page.querySelector('.sd-alert--warning')?.textContent).toContain('initial password');
  });

  it('logs out and returns to the login page', async () => {
    const fixture = await signedInAs();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    expect(TestBed.inject(AuthService).isSignedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
