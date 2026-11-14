import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { homeFor } from '../../../core/auth/auth.guards';
import { passwordRules } from '../../../core/auth/password-rules';
import { PHONE_E164 } from '../../../shared/models/application';
import { AuthService, GOOGLE_LOGIN_URL } from '../../../core/auth/auth.service';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';

type Field = 'fullName' | 'email' | 'phoneNumber' | 'password';


@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register-page.html',
  styleUrl: './register-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  protected readonly googleLoginUrl = GOOGLE_LOGIN_URL;
  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly serverError = signal<string | null>(null);
  private readonly errorAlert = viewChild<ElementRef<HTMLElement>>('errorAlert');

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phoneNumber: ['', Validators.pattern(PHONE_E164)],
    password: ['', [Validators.required, passwordRules]],
  });

  protected showError(name: Field): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected describedBy(name: Field, helpId?: string): string | null {
    const ids = [helpId, this.showError(name) ? `register-${name}-error` : null].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  }

  protected submit(): void {
    this.submitted.set(true);
    this.serverError.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const value = this.form.getRawValue();
    this.auth
      .register({
        fullName: value.fullName.trim(),
        email: value.email.trim(),
        password: value.password,
        phoneNumber: value.phoneNumber.trim() || undefined,
      })
      .subscribe({
        next: (user) => void this.router.navigateByUrl(homeFor(user)),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private handleError(error: unknown): void {
    this.submitting.set(false);
    const problem = readProblem(error);
    if (problem.code === 'EMAIL_ALREADY_REGISTERED') {
      // Cleared as soon as the email is edited, because validation runs again.
      this.form.controls.email.setErrors({ taken: true });
      return;
    }
    if (problem.code === 'VALIDATION_FAILED' && problem.errors?.length) {
      // The server's rules disagreed with ours; show its messages on the matching fields.
      for (const { field, message } of problem.errors) {
        this.form.get(field)?.setErrors({ server: message });
      }
      return;
    }
    this.serverError.set(errorMessage(error));
    afterNextRender(() => this.errorAlert()?.nativeElement.focus(), { injector: this.injector });
  }
}
