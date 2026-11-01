import { AuthService } from './core/auth/auth.service';
import { canVisit, homeFor } from './core/auth/auth-routing';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  constructor() {
    effect(() => {
      const user = this.auth.user();
      const path = this.router.url.split(/[?#]/)[0];
      // Login/callback components own their redirects (including an authorized return URL).
      if (['/', '/login', '/register', '/auth/callback'].includes(path)) return;
      if (!canVisit(user, this.router.url)) void this.router.navigateByUrl(homeFor(user));
    });
  }
}
