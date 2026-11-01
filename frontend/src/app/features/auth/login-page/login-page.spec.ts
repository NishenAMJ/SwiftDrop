import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problem, tokenResponse } from '../../../testing/auth-fixtures';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function fill(email: string, password: string) {
    const emailInput = page.querySelector<HTMLInputElement>('#login-email')!;
    const passwordInput = page.querySelector<HTMLInputElement>('#login-password')!;
    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
  }

  async function submit() {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('labels every field and offers Google login and registration', () => {
    expect(page.querySelector('label[for="login-email"]')?.textContent).toContain('Email');
    expect(page.querySelector('label[for="login-password"]')?.textContent).toContain('Password');
    expect(page.querySelector('a[href="/oauth2/authorization/google"]')).not.toBeNull();
    expect(page.querySelector('a[href="/register"]')).not.toBeNull();
  });

  it('shows field errors on submit without calling the server', async () => {
    await submit();

    const email = page.querySelector('#login-email')!;
    expect(email.getAttribute('aria-invalid')).toBe('true');
    expect(email.getAttribute('aria-describedby')).toBe('login-email-error');
    expect(page.querySelector('#login-email-error')?.textContent).toContain(
      'Enter your email address.',
    );
    expect(page.querySelector('#login-password-error')?.textContent).toContain(
      'Enter your password.',
    );
  });

  it('logs in and goes to the account page', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill(' nimal@example.com ', 'Secret-pass-1');
    await submit();

    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual({ email: 'nimal@example.com', password: 'Secret-pass-1' });
    expect(page.querySelector('button[type="submit"]')?.textContent).toContain('Logging in');
    request.flush(tokenResponse());

    expect(navigate).toHaveBeenCalledWith('/customer');
  });

  it('explains a wrong password in its own words and keeps what was typed', async () => {
    fill('nimal@example.com', 'Wrong-pass-1');
    await submit();
    http
      .expectOne('/api/v1/auth/login')
      .flush(problem('INVALID_CREDENTIALS', { detail: 'server text' }), {
        status: 401,
        statusText: 'Unauthorized',
      });
    await fixture.whenStable();

    const alert = page.querySelector('[role="alert"]');
    expect(alert?.textContent?.trim()).toBe('Email or password is incorrect.');
    expect(page.querySelector<HTMLInputElement>('#login-email')!.value).toBe('nimal@example.com');
    expect(page.querySelector('button[type="submit"]')?.hasAttribute('disabled')).toBe(false);
  });
});
