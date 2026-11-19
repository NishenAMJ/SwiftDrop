/** Shapes from contracts/openapi/identity-merchant-service.yaml. */

export type Role = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'DISPATCHER';

export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
  COURIER: 'Courier',
  DISPATCHER: 'Dispatcher',
};

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

export interface ChangePasswordRequest {
  /** Omitted when a Google-only account sets its first password. */
  currentPassword?: string;
  newPassword: string;
}

/** One page of a list (contracts/README.md, "Paging"). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 9457 Problem Details with SwiftDrop's code and field errors. */
export interface Problem {
  code?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}
