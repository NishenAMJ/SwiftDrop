import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { homeFor } from '../../auth/auth.guards';
import { ROLE_LABELS, Role } from '../../auth/auth.models';
import { AuthService } from '../../auth/auth.service';

interface NavItem {
  label: string;
  path: string;
}

interface NavGroup {
  heading: string;
  items: NavItem[];
}

/** Most specialised first: the label shown next to the user's name. */
const ROLE_PRIORITY: readonly Role[] = ['DISPATCHER', 'MERCHANT', 'COURIER', 'CUSTOMER'];

/**
 * Frame for signed-in screens: header, role-aware navigation and the page. Shows only the links
 * for roles the user's token carries (frontend/style.md section 6); the backend still checks every
 * request. Below 1024px the sidebar becomes a menu opened from the header.
 */
@Component({
  selector: 'app-app-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-layout.html',
  styleUrl: './app-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly menuOpen = signal(false);
  protected readonly signingOut = signal(false);

  protected readonly home = computed(() => homeFor(this.user()));

  protected readonly roleLabel = computed(() => {
    const roles = this.auth.roles();
    const main = ROLE_PRIORITY.find((role) => roles.includes(role));
    return main ? ROLE_LABELS[main] : 'Account';
  });

  protected readonly navGroups = computed<NavGroup[]>(() => {
    const has = (role: Role) => this.auth.roles().includes(role);
    const groups: NavGroup[] = [];

    if (has('DISPATCHER')) {
      groups.push({
        heading: 'Dispatcher',
        items: [
          { label: 'Merchant applications', path: '/dispatcher/merchants' },
          { label: 'Courier applications', path: '/dispatcher/couriers' },
          { label: 'Users', path: '/dispatcher/users' },
          { label: 'Tracking reports', path: '/tracking/reports' },
        ],
      });
    }
    if (has('MERCHANT')) {
      groups.push({
        heading: 'Merchant',
        items: [
          { label: 'API keys', path: '/merchant/api-keys' },
          { label: 'Merchant account', path: '/merchant/application' },
        ],
      });
    }
    if (has('COURIER')) {
      groups.push({
        heading: 'Courier',
        items: [{ label: 'Courier account', path: '/courier/application' }],
      });
    }
    if (has('CUSTOMER')) {
      const items: NavItem[] = [{ label: 'Customer portal', path: '/customer' }];
      // Approved partners find their application under their own group above.
      if (!has('MERCHANT')) {
        items.push({ label: 'Become a merchant', path: '/merchant/application' });
      }
      if (!has('COURIER')) {
        items.push({ label: 'Become a courier', path: '/courier/application' });
      }
      groups.push({ heading: 'Customer', items });
    }
    groups.push({ heading: 'Account', items: [{ label: 'Your account', path: '/account' }] });
    return groups;
  });

  constructor() {
    // Close the mobile menu once a link has been followed.
    const subscription = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.menuOpen.set(false));
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  /** With <base href="/">, a plain "#id" link would leave the page; move focus instead. */
  protected skipToMain(event: Event, main: HTMLElement): void {
    event.preventDefault();
    main.focus();
  }

  protected logout(): void {
    if (this.signingOut()) {
      return;
    }
    this.signingOut.set(true);
    // Logged out locally either way; the cookie expires on its own.
    const done = () => void this.router.navigateByUrl('/login');
    this.auth.logout().subscribe({ complete: done, error: done });
  }
}
