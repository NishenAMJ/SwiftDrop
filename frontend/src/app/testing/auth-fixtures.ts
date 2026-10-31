import { AuthUser, TokenResponse } from '../core/auth/auth.models';

/** Example data for tests, shaped like identity's responses. */
export const exampleUser: AuthUser = {
  id: '8f3c2a10-4b7d-4e9a-a1c2-5d6e7f809a1b',
  email: 'nimal@example.com',
  fullName: 'Nimal Perera',
  phoneNumber: '+94771234567',
  roles: ['CUSTOMER'],
  status: 'ACTIVE',
  authProviders: ['PASSWORD'],
  mustChangePassword: false,
  createdAt: '2026-11-01T09:30:00Z',
};

export function tokenResponse(user: AuthUser = exampleUser, accessToken = 'access-1'): TokenResponse {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, user };
}

export function problem(code: string, extra: object = {}): object {
  return { type: 'https://swiftdrop.dev/problems/x', title: 'Error', status: 400, code, ...extra };
}
