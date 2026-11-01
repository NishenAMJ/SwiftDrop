import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problem, tokenResponse } from '../../../testing/auth-fixtures';
import { RegisterPage } from './register-page';

describe('RegisterPage', () => {
  let fixture: ComponentFixture<RegisterPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RegisterPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function type(id: string, value: string) {
    const input = page.querySelector<HTMLInputElement>(`#register-${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  async function submit() {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function fillValid() {
    type('fullName', 'Nimal Perera');
    type('email', 'nimal@example.com');
    type('password', 'Secret-pass-1');
  }

  it('applies the same password and phone rules as the server', async () => {
    fillValid();
    type('password', 'lettersonly');
    type('phoneNumber', '0771234567');
    await fixture.whenStable();

    expect(page.querySelector('#register-password-error')?.textContent).toContain(
      'one letter and one number',
    );
    expect(page.querySelector('#register-phoneNumber-error')?.textContent).toContain(
      '+94771234567',
    );
    expect(page.querySelector('#register-password')?.getAttribute('aria-describedby')).toBe(
      'register-password-help register-password-error',
    );

    await submit();
    http.expectNone('/api/v1/auth/register');
  });

  it('creates the account without an empty phone number', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fillValid();
    await submit();

    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({
      role: 'CUSTOMER',
      fullName: 'Nimal Perera',
      email: 'nimal@example.com',
      password: 'Secret-pass-1',
      phoneNumber: undefined,
    });
    request.flush(tokenResponse(), { status: 201, statusText: 'Created' });
    expect(navigate).toHaveBeenCalledWith('/customer');
  });

  it('points to login when the email is already registered, until the email changes', async () => {
    fillValid();
    await submit();
    http.expectOne('/api/v1/auth/register').flush(problem('EMAIL_ALREADY_REGISTERED'), {
      status: 409,
      statusText: 'Conflict',
    });
    await fixture.whenStable();

    const error = page.querySelector('#register-email-error');
    expect(error?.textContent).toContain('already exists');
    expect(error?.querySelector('a[href="/login"]')).not.toBeNull();

    type('email', 'other@example.com');
    await fixture.whenStable();
    expect(page.querySelector('#register-email-error')).toBeNull();
  });

  it('shows server field errors on the matching field', async () => {
    fillValid();
    await submit();
    http
      .expectOne('/api/v1/auth/register')
      .flush(
        problem('VALIDATION_FAILED', {
          errors: [{ field: 'fullName', message: 'must not be blank' }],
        }),
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(page.querySelector('#register-fullName-error')?.textContent).toContain(
      'must not be blank',
    );
  });
});
