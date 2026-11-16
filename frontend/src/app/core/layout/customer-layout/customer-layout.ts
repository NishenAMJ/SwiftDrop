import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';
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

  readonly displayName = this.profileService.customerName;
  readonly displayEmail = this.profileService.customerEmail;
  readonly displayAvatar = this.profileService.customerAvatar;
  readonly displayInitials = this.profileService.customerInitials;
  readonly currentYear = new Date().getFullYear();

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
      // logout() is an Observable: it only runs when subscribed. The local session is cleared
      // even if the request fails, and the cookie expires on its own.
      await firstValueFrom(this.auth.logout()).catch(() => undefined);
      await this.router.navigate(['/login']);
    } finally {
      this.loggingOut.set(false);
    }
  }
}
