import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { HOME_AFTER_LOGIN } from '../../../core/auth/auth.guards';
import { AuthService } from '../../../core/auth/auth.service';

/** Wording for the ?error= values identity sends back after Google login. */
const GOOGLE_ERRORS: Record<string, string> = {
  email_not_verified:
    "Google hasn't confirmed this email address. Verify it with Google, or create an account with a password.",
  account_suspended: 'This account is suspended. Contact SwiftDrop support.',
  account_mismatch:
    'This email is already linked to a different Google account. Use that Google account, or log in with your password.',
};
const FALLBACK = "Google login didn't finish. Please try again.";

/**
 * Where identity sends the browser after Google login. On success the refresh cookie is already
 * set, so one refresh call gives the first access token.
 */
@Component({
  selector: 'app-auth-callback-page',
  imports: [RouterLink],
  templateUrl: './auth-callback-page.html',
  styleUrl: './auth-callback-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthCallbackPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const reason = this.route.snapshot.queryParamMap.get('error');
    if (reason) {
      this.error.set(GOOGLE_ERRORS[reason] ?? FALLBACK);
      return;
    }
    this.auth.refresh().subscribe({
      next: () => void this.router.navigateByUrl(HOME_AFTER_LOGIN, { replaceUrl: true }),
      error: () => this.error.set(FALLBACK),
    });
  }
}
