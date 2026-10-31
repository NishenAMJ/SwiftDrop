/** Shapes from contracts/openapi/identity-merchant-service.yaml. */

export type Role = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'DISPATCHER';

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
  roles: Role[];
  status: 'ACTIVE' | 'SUSPENDED';
  authProviders: ('PASSWORD' | 'GOOGLE')[];
  mustChangePassword: boolean;
  merchantId?: string;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user?: AuthUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phoneNumber?: string;
}

/** RFC 9457 Problem Details with SwiftDrop's code and field errors. */
export interface Problem {
  code?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}
