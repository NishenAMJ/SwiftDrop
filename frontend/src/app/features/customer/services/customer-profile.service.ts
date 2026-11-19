import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, Subscription, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CustomerProfile, UpdateProfileRequest } from '../models/customer-profile.models';

export const PROFILE_API = '/api/v1/customer/profile';
const emptyProfile = (): CustomerProfile => ({ customerId: '', fullName: '', email: '' });
const text = (value: unknown): string => typeof value === 'string' ? value.trim() : '';
export function profileInitials(name: string | null | undefined): string {
  const parts = text(name).split(/\s+/).filter(Boolean);
  return parts.length > 1 ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase() : (parts[0]?.slice(0, 2).toUpperCase() ?? '');
}

@Injectable({ providedIn: 'root' })
export class CustomerProfileService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly savedProfile = signal<CustomerProfile>(emptyProfile());
  private fetch?: Subscription;
  private activeCustomerId: string | null = null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Identity and extended profile are separate DB-backed APIs. Never reuse another user's cache.
  readonly profile = computed<CustomerProfile>(() => {
    const user = this.auth.user();
    if (!user) return emptyProfile();
    const saved = this.savedProfile();
    const p = saved.customerId === user.id ? saved : emptyProfile();
    return {
      customerId: user.id,
      fullName: text(p.fullName) || text(user.fullName),
      email: text(p.email) || text(user.email),
      phoneNumber: text(p.phoneNumber) || text(user.phoneNumber),
      streetAddress: text(p.streetAddress), city: text(p.city), postalCode: text(p.postalCode),
      avatarBase64: text(p.avatarBase64) || undefined, updatedAt: p.updatedAt,
    };
  });
  readonly customerName = computed(() => this.profile().fullName);
  readonly customerEmail = computed(() => this.profile().email);
  readonly customerAvatar = computed(() => this.profile().avatarBase64);
  readonly customerInitials = computed(() => profileInitials(this.customerName()));

  constructor() {
    // Old global caches included demo data and were not bound to an authenticated customer.
    try { localStorage.removeItem('swiftdrop_customer_profile'); localStorage.removeItem('swiftdrop_customer_profile_v2'); } catch {}
    this.refreshProfile();
    effect(() => {
      const id = this.auth.user()?.id ?? null;
      if (id !== this.activeCustomerId) this.refreshProfile();
    });
  }

  refreshProfile(): void {
    this.fetch?.unsubscribe();
    const id = this.auth.user()?.id ?? null;
    if (id !== this.activeCustomerId) this.savedProfile.set(emptyProfile());
    this.activeCustomerId = id;
    this.error.set(null);
    if (!id) { this.loading.set(false); return; }
    this.loading.set(true);
    this.fetch = this.http.get<CustomerProfile>(PROFILE_API).subscribe({
      next: data => {
        if (this.auth.user()?.id !== id) return;
        this.loading.set(false);
        if (!data || data.customerId !== id) { this.error.set('Could not load your profile details. Please retry.'); return; }
        this.savedProfile.set(data);
      },
      error: () => {
        if (this.auth.user()?.id !== id) return;
        this.loading.set(false); this.error.set('Could not load your profile details. Please retry.');
      },
    });
  }

  updateProfile(request: UpdateProfileRequest): Observable<CustomerProfile> {
    const id = this.auth.user()?.id;
    // Errors propagate to the form. An offline/local edit is never presented as a saved DB record.
    return this.http.put<CustomerProfile>(PROFILE_API, request).pipe(tap(updated => {
      if (id && this.auth.user()?.id === id && updated.customerId === id) this.savedProfile.set(updated);
    }));
  }
}
