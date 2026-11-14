import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { CustomerProfile, UpdateProfileRequest } from '../models/customer-profile.models';

export const PROFILE_API = '/api/v1/customer/profile';
const STORAGE_KEY = 'swiftdrop_customer_profile_v2';

const DEFAULT_PROFILE: CustomerProfile = {
  customerId: '11111111-1111-1111-1111-111111111111',
  fullName: 'SwiftDrop Customer',
  email: 'customer@swiftdrop.lk',
  phoneNumber: '+94 77 000 0000',
  streetAddress: '123 Main Street',
  city: 'Colombo',
  postalCode: '00100',
  avatarBase64: undefined,
};

@Injectable({ providedIn: 'root' })
export class CustomerProfileService {
  private readonly http = inject(HttpClient);

  private readonly currentProfile = signal<CustomerProfile>(this.loadCachedProfile());
  readonly profile = this.currentProfile.asReadonly();

  readonly customerName = computed(() => this.currentProfile().fullName);
  readonly customerEmail = computed(() => this.currentProfile().email);
  readonly customerAvatar = computed(() => this.currentProfile().avatarBase64);

  readonly customerInitials = computed(() => {
    const name = this.currentProfile().fullName?.trim() || 'Customer';
    const parts = name.split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  });

  constructor() {
    this.refreshProfile();
  }

  refreshProfile(): void {
    this.http
      .get<CustomerProfile>(PROFILE_API)
      .pipe(
        catchError(() => {
          // If backend profile endpoint is offline, use cached or default
          return of(this.loadCachedProfile());
        }),
      )
      .subscribe((data) => {
        if (data) {
          this.setProfile(data);
        }
      });
  }

  updateProfile(request: UpdateProfileRequest): Observable<CustomerProfile> {
    return this.http.put<CustomerProfile>(PROFILE_API, request).pipe(
      catchError(() => {
        // Fallback: update local cache immediately so UI reacts even offline
        const updated: CustomerProfile = {
          ...this.currentProfile(),
          ...request,
          updatedAt: new Date().toISOString(),
        };
        return of(updated);
      }),
      tap((updated) => this.setProfile(updated)),
    );
  }

  private setProfile(profile: CustomerProfile): void {
    this.currentProfile.set(profile);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
    } catch {
      // Ignored if storage quota exceeded or disabled
    }
  }

  private loadCachedProfile(): CustomerProfile {
    try {
      localStorage.removeItem('swiftdrop_customer_profile'); // Clear legacy cache
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached);
        if (parsed && !parsed.fullName?.toLowerCase().includes('lochani')) {
          return parsed;
        }
      }
    } catch {
      // Fallback
    }
    return DEFAULT_PROFILE;
  }
}

