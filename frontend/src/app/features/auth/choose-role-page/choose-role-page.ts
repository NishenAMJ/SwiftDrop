import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { PublicRole } from '../../../core/auth/auth.models';
import { homeFor } from '../../../core/auth/auth-routing';
import { errorMessage } from '../../../core/auth/problem-messages';
@Component({
  selector: 'app-choose-role-page',
  imports: [ReactiveFormsModule],
  templateUrl: './choose-role-page.html',
  styleUrl: './choose-role-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChooseRolePage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly form = inject(NonNullableFormBuilder).group({
    role: ['CUSTOMER' as PublicRole, Validators.required],
  });
  protected submit(): void {
    if (this.busy() || this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    this.auth.selectRole(this.form.getRawValue().role).subscribe({
      next: (user) => void this.router.navigateByUrl(homeFor(user)),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
  protected logout(): void {
    this.auth
      .logout()
      .subscribe({
        next: () => void this.router.navigateByUrl('/login'),
        error: () => void this.router.navigateByUrl('/login'),
      });
  }
}
