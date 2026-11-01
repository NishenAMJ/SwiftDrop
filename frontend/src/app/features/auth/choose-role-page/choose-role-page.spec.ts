import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ChooseRolePage } from './choose-role-page';
import { exampleUser, tokenResponse } from '../../../testing/auth-fixtures';

describe('Google role selection', () => {
  it('submits the selected role and routes to onboarding', async () => {
    TestBed.configureTestingModule({
      imports: [ChooseRolePage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChooseRolePage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const select = page.querySelector('select')!;
    select.value = 'MERCHANT';
    select.dispatchEvent(new Event('change'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/v1/users/me/role');
    expect(request.request.body).toEqual({ role: 'MERCHANT' });
    request.flush(
      tokenResponse({
        ...exampleUser,
        accountRole: 'MERCHANT',
        roles: [],
        onboardingState: 'APPLICATION_REQUIRED',
      }),
    );
    expect(navigate).toHaveBeenCalledWith('/onboarding');
    http.verify();
  });
});
