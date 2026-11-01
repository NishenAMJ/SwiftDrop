import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Role } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';

const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
  COURIER: 'Courier',
  DISPATCHER: 'Dispatcher',
};

/**
 * The page people land on after logging in, until each role has its own dashboard: who is
 * signed in, their roles, and log out.
 */
@Component({
  selector: 'app-account-page',
  templateUrl: './account-page.html',
  styleUrl: './account-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly roles = computed(() => (this.user()?.roles ?? []).map((r) => ROLE_LABELS[r]));
  protected readonly signingOut = signal(false);

  protected logout(): void {
    this.signingOut.set(true);
    this.auth.logout().subscribe({
      complete: () => void this.router.navigateByUrl('/login'),
      // Logged out locally either way; the cookie expires on its own.
      error: () => void this.router.navigateByUrl('/login'),
    });
  }
}
