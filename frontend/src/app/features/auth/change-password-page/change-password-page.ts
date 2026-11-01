import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { homeFor } from '../../../core/auth/auth-routing';
import { errorMessage } from '../../../core/auth/problem-messages';
@Component({
  selector: 'app-change-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './change-password-page.html',
  styleUrl: './change-password-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordPage {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly fields = ['currentPassword', 'newPassword', 'confirmPassword'] as const;
  protected readonly labels = {
    currentPassword: 'Current password',
    newPassword: 'New password',
    confirmPassword: 'Confirm new password',
  };
  protected readonly hasPassword = this.auth.user()?.authProviders.includes('PASSWORD') ?? true;
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      currentPassword: ['', this.hasPassword ? [Validators.required] : []],
      newPassword: [
        '',
        [
          Validators.required,
          (c: AbstractControl<string>) => {
            const value = c.value ?? '';
            return [...value].length >= 8 &&
              [...value].length <= 64 &&
              /\p{L}/u.test(value) &&
              /\p{N}/u.test(value)
              ? null
              : { password: true };
          },
        ],
      ],
      confirmPassword: ['', Validators.required],
    },
    {
      validators: (c) =>
        c.get('newPassword')?.value === c.get('confirmPassword')?.value ? null : { mismatch: true },
    },
  );
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.busy() || this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    this.auth.changePassword(value.currentPassword || undefined, value.newPassword).subscribe({
      next: (user) => void this.router.navigateByUrl(homeFor(user)),
      error: (e) => {
        this.busy.set(false);
        this.error.set(
          errorMessage(e, { INVALID_CREDENTIALS: 'The current password is incorrect.' }),
        );
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
