export interface CustomerProfile {
  customerId: string;
  fullName: string;
  email: string;
  phoneNumber?: string;
  streetAddress?: string;
  city?: string;
  postalCode?: string;
  avatarBase64?: string;
  updatedAt?: string;
}

export interface UpdateProfileRequest {
  fullName: string;
  email: string;
  phoneNumber?: string;
  streetAddress?: string;
  city?: string;
  postalCode?: string;
  avatarBase64?: string;
}

