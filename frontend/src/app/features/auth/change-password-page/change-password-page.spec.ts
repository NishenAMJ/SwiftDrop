import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ChangePasswordPage } from './change-password-page';
import { AuthService } from '../../../core/auth/auth.service';
import { exampleUser, tokenResponse } from '../../../testing/auth-fixtures';

describe('required password change', () => {
  it('validates confirmation, changes password, and refreshes before entering the dashboard', async () => {
    TestBed.configureTestingModule({
      imports: [ChangePasswordPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const user = {
      ...exampleUser,
      accountRole: 'DISPATCHER' as const,
      roles: ['DISPATCHER' as const],
      mustChangePassword: true,
    };
    TestBed.inject(AuthService).login({ email: user.email, password: 'Initial-123' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(user));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChangePasswordPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const type = (id: string, value: string) => {
      const field = page.querySelector<HTMLInputElement>('#' + id)!;
      field.value = value;
      field.dispatchEvent(new Event('input'));
    };
    type('currentPassword', 'Initial-123');
    type('newPassword', 'New-password-123');
    type('confirmPassword', 'Mismatch-123');
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectNone('/api/v1/users/me/password');
    type('confirmPassword', 'New-password-123');
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/v1/users/me/password');
    expect(request.request.body).toEqual({
      currentPassword: 'Initial-123',
      newPassword: 'New-password-123',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush(tokenResponse({ ...user, mustChangePassword: false }));
    expect(navigate).toHaveBeenCalledWith('/dispatcher');
    http.verify();
  });
});
