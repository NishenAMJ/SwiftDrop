import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { CustomerProfileService } from '../../../features/customer/services/customer-profile.service';

@Component({
  selector: 'app-customer-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './customer-layout.html',
  styleUrl: './customer-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly profileService = inject(CustomerProfileService);

  readonly user = this.auth.user;
  readonly profile = this.profileService.profile;
  readonly mobileMenuOpen = signal(false);
  readonly loggingOut = signal(false);

  readonly displayName = computed(() => {
    return this.user()?.fullName || this.profile().fullName || 'SwiftDrop Customer';
  });

  readonly displayEmail = computed(() => {
    return this.user()?.email || this.profile().email || 'customer@swiftdrop.lk';
  });

  readonly displayAvatar = computed(() => {
    return this.profile().avatarBase64 || null;
  });

  readonly displayInitials = computed(() => {
    const name = this.displayName().trim();
    const parts = name.split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  });

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  async logout(): Promise<void> {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    try {
      await this.auth.logout();
      await this.router.navigate(['/login']);
    } finally {
      this.loggingOut.set(false);
    }
  }
}
