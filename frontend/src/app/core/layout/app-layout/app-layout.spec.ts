import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { exampleUser, tokenResponse } from '../../../testing/auth-fixtures';
import { AuthUser } from '../../auth/auth.models';
import { AuthService } from '../../auth/auth.service';
import { AppLayout } from './app-layout';

describe('AppLayout', () => {
  let http: HttpTestingController;

  async function signedInAs(user: AuthUser) {
    TestBed.configureTestingModule({
      imports: [AppLayout],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ email: user.email, password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(user));
    const fixture = TestBed.createComponent(AppLayout);
    await fixture.whenStable();
    return fixture;
  }

  function links(page: HTMLElement): string[] {
    return [...page.querySelectorAll('nav a')].map((a) => a.textContent!.trim());
  }

  afterEach(() => http.verify());

  it('shows a customer how to become a merchant or courier', async () => {
    const page = (await signedInAs(exampleUser)).nativeElement as HTMLElement;
    expect(links(page)).toEqual(['Customer portal', 'Become a merchant', 'Become a courier', 'Your account']);
    expect(page.querySelector('.app-header__role')?.textContent).toContain('Customer');
  });

  it('shows a merchant their keys instead of the merchant application', async () => {
    const page = (await signedInAs({ ...exampleUser, roles: ['CUSTOMER', 'MERCHANT'] }))
      .nativeElement as HTMLElement;
    expect(links(page)).toEqual([
      'API keys',
      'Merchant account',
      'Customer portal',
      'Become a courier',
      'Your account',
    ]);
    expect(page.querySelector('.app-header__role')?.textContent).toContain('Merchant');
  });

  it('shows dispatchers their tools and nothing for customers', async () => {
    const page = (await signedInAs({ ...exampleUser, roles: ['DISPATCHER'] })).nativeElement as HTMLElement;
    expect(links(page)).toEqual([
      'Merchant applications',
      'Courier applications',
      'Users',
      'Tracking reports',
      'Your account',
    ]);
  });

  it('hides dispatcher tools until the initial password is replaced', async () => {
    const page = (await signedInAs({ ...exampleUser, roles: ['DISPATCHER'], mustChangePassword: true }))
      .nativeElement as HTMLElement;
    expect(links(page)).toEqual(['Your account']);
  });

  it('opens the menu with a button that reports its state', async () => {
    const fixture = await signedInAs(exampleUser);
    const page = fixture.nativeElement as HTMLElement;
    const toggle = page.querySelector<HTMLButtonElement>('.app-header__menu')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe('app-nav');

    toggle.click();
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(page.querySelector('#app-nav')?.classList).toContain('app-nav--open');
  });

  it('logs out and returns to the login page', async () => {
    const fixture = await signedInAs(exampleUser);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    const logout = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Log out'),
    )!;
    logout.click();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    expect(TestBed.inject(AuthService).isSignedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
