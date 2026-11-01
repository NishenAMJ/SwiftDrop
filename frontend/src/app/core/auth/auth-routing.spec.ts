import { canVisit, destinationFor, homeFor } from './auth-routing';
import { exampleUser } from '../../testing/auth-fixtures';
import { AuthUser, Role } from './auth.models';

describe('role routing', () => {
  it('routes each active role to its own dashboard only', () => {
    for (const role of ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER'] as Role[]) {
      const user: AuthUser = { ...exampleUser, accountRole: role, roles: [role] };
      expect(homeFor(user)).toBe('/' + role.toLowerCase());
      for (const other of ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER']) {
        expect(canVisit(user, '/' + other.toLowerCase())).toBe(role === other);
      }
    }
  });
  it('prioritizes forced password change and role selection', () => {
    expect(homeFor({ ...exampleUser, mustChangePassword: true })).toBe('/change-password');
    expect(
      homeFor({
        ...exampleUser,
        accountRole: undefined,
        roles: [],
        onboardingState: 'ROLE_REQUIRED',
      }),
    ).toBe('/choose-role');
  });
  it('restricts applicants in every non-approved state', () => {
    for (const onboardingState of [
      'APPLICATION_REQUIRED',
      'PENDING',
      'REJECTED',
      'SUSPENDED',
    ] as const) {
      const user: AuthUser = {
        ...exampleUser,
        accountRole: 'MERCHANT',
        roles: [],
        onboardingState,
      };
      expect(homeFor(user)).toBe('/onboarding');
      expect(canVisit(user, '/merchant')).toBe(false);
      expect(canVisit(user, '/customer')).toBe(false);
    }
  });
  it('honors only authorized return paths', () => {
    expect(destinationFor(exampleUser, '/account')).toBe('/account');
    for (const url of [
      '/dispatcher',
      '/merchant',
      '//evil.example',
      'https://evil.example',
      '/choose-role',
    ]) {
      expect(destinationFor(exampleUser, url)).toBe('/customer');
    }
    expect(homeFor(null)).toBe('/login');
    expect(canVisit(null, '/customer')).toBe(false);
  });
});
