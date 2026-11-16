import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { homeFor } from '../../../core/auth/auth.guards';
import { ROLE_LABELS } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { PASSWORD_HELP, passwordRuleMessage, passwordRules } from '../../../core/auth/password-rules';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { applyServerErrors, focusFirstInvalid, shouldShowError } from '../../../shared/forms/form-errors';
import { PasswordField } from '../../../shared/components/password-field/password-field';

type PasswordControl = 'currentPassword' | 'newPassword' | 'confirmPassword';

function matchesNewPassword(control: AbstractControl<string>): ValidationErrors | null {
  const newPassword = control.parent?.get('newPassword')?.value;
  return control.value && newPassword && control.value !== newPassword ? { mismatch: true } : null;
}

/**
 * The signed-in user's account: details, roles, password, and ways to work with SwiftDrop as a
 * merchant or courier. A new dispatcher lands here first to replace the initial password.
 */
@Component({
  selector: 'app-account-page',
  imports: [RouterLink, ReactiveFormsModule, PasswordField],
  templateUrl: './account-page.html',
  styleUrl: './account-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly user = this.auth.user;
  protected readonly passwordHelp = PASSWORD_HELP;
  protected readonly roles = computed(() => (this.user()?.roles ?? []).map((r) => ROLE_LABELS[r]));
  protected readonly isCustomer = computed(() => this.auth.roles().includes('CUSTOMER'));
  protected readonly isMerchant = computed(() => this.auth.roles().includes('MERCHANT'));
  protected readonly isCourier = computed(() => this.auth.roles().includes('COURIER'));

  /** Google-only accounts have no password yet, so there is no current one to ask for. */
  protected readonly hasPassword = computed(() => this.user()?.authProviders.includes('PASSWORD') ?? false);

  /**
   * Set by roleGuard when it sent the user here from a page they can't open. Read as a stream,
   * because the guard can redirect here while this page is already showing.
   */
  protected readonly forbidden = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((params) => params.get('notice') === 'forbidden')),
    { initialValue: false },
  );

  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly passwordNotice = signal<string | null>(null);
  private readonly passwordAlert = viewChild<ElementRef<HTMLElement>>('passwordAlert');

  protected readonly form = inject(NonNullableFormBuilder).group({
    currentPassword: [''],
    newPassword: ['', [Validators.required, passwordRules]],
    confirmPassword: ['', [Validators.required, matchesNewPassword]],
  });

  constructor() {
    // Re-check the confirmation whenever the new password changes.
    this.form.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false }));
  }

  protected error(field: PasswordControl): string | null {
    const control = this.form.controls[field];
    if (!shouldShowError(control, this.submitted())) {
      return null;
    }
    const errors = control.errors ?? {};
    if (errors['server']) {
      return errors['server'];
    }
    if (errors['wrong']) {
      return 'Your current password is incorrect.';
    }
    if (errors['required']) {
      return {
        currentPassword: 'Enter your current password.',
        newPassword: 'Enter a new password.',
        confirmPassword: 'Enter the new password again.',
      }[field];
    }
    if (errors['mismatch']) {
      return "The passwords don't match.";
    }
    return passwordRuleMessage(errors) ?? 'Check this field.';
  }

  protected changePassword(): void {
    // Required only when the account already has a password.
    const current = this.form.controls.currentPassword;
    current.setValidators(this.hasPassword() ? Validators.required : null);
    current.updateValueAndValidity();

    this.submitted.set(true);
    this.passwordError.set(null);
    this.passwordNotice.set(null);
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }

    const wasLocked = this.user()?.mustChangePassword ?? false;
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.saving.set(true);
    this.auth
      .changePassword({
        currentPassword: this.hasPassword() ? currentPassword : undefined,
        newPassword,
      })
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.submitted.set(false);
          this.form.reset();
          if (wasLocked) {
            // The new token carries the dispatcher role: go to the dispatcher tools.
            void this.router.navigateByUrl(homeFor(user));
            return;
          }
          this.passwordNotice.set('Password changed. You have been logged out on your other devices.');
        },
        error: (error: unknown) => this.handlePasswordError(error),
      });
  }

  private handlePasswordError(error: unknown): void {
    this.saving.set(false);
    const problem = readProblem(error);
    if (problem.code === 'INVALID_CREDENTIALS') {
      this.form.controls.currentPassword.setErrors({ wrong: true });
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    if (applyServerErrors(this.form, problem)) {
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.passwordError.set(errorMessage(error));
    afterNextRender(() => this.passwordAlert()?.nativeElement.focus(), { injector: this.injector });
  }
}
