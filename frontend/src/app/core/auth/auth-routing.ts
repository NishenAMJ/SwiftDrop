import { AuthUser } from './auth.models';

export function homeFor(user: AuthUser | null): string {
  if (!user) return '/login';
  if (user.mustChangePassword) return '/change-password';
  if (!user.accountRole || user.onboardingState === 'ROLE_REQUIRED') return '/choose-role';
  if (user.onboardingState !== 'READY' || !user.roles.includes(user.accountRole))
    return '/onboarding';
  return '/' + user.accountRole.toLowerCase();
}

export function canVisit(user: AuthUser | null, url: string): boolean {
  if (!user || user.status !== 'ACTIVE') return false;
  const path = url.split(/[?#]/)[0];
  if (path === homeFor(user)) return true;
  return (
    homeFor(user) === '/' + user.accountRole?.toLowerCase() &&
    (path === '/account' || path === '/change-password')
  );
}

export function destinationFor(user: AuthUser | null, returnUrl?: string | null): string {
  return returnUrl &&
    returnUrl.startsWith('/') &&
    !returnUrl.startsWith('//') &&
    !returnUrl.includes('\\') &&
    canVisit(user, returnUrl)
    ? returnUrl
    : homeFor(user);
}
