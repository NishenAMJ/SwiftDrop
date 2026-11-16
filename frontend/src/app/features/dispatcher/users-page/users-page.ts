import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthUser, Page, ROLE_LABELS, Role } from '../../../core/auth/auth.models';
import { PASSWORD_HELP, passwordRuleMessage, passwordRules } from '../../../core/auth/password-rules';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { Pagination } from '../../../shared/components/pagination/pagination';
import {
  FieldMessages,
  applyServerErrors,
  fieldErrorMessage,
  focusFirstInvalid,
} from '../../../shared/forms/form-errors';
import { PHONE_E164 } from '../../../shared/models/application';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { DispatcherService } from '../dispatcher.service';
import { PasswordField } from '../../../shared/components/password-field/password-field';

const PAGE_SIZE = 20;

type NewDispatcherField = 'fullName' | 'email' | 'phoneNumber' | 'initialPassword';

const MESSAGES: Record<NewDispatcherField, FieldMessages> = {
  fullName: { required: 'Enter their full name.' },
  email: { required: 'Enter their email address.' },
  phoneNumber: { required: '', pattern: 'Enter the number with its country code, like +94771234567.' },
  initialPassword: { required: 'Choose an initial password.' },
};

const PROVIDER_LABELS: Record<string, string> = { PASSWORD: 'Password', GOOGLE: 'Google' };

/**
 * Every SwiftDrop account, for dispatchers: search, filter by role or status, and add another
 * dispatcher. Dispatcher accounts can only be created here (or by the startup bootstrap).
 */
@Component({
  selector: 'app-users-page',
  imports: [ReactiveFormsModule, Pagination, ColomboDatePipe, PasswordField],
  templateUrl: './users-page.html',
  styleUrl: './users-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage implements OnInit {
  private readonly dispatcher = inject(DispatcherService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly roles = Object.entries(ROLE_LABELS) as [Role, string][];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly passwordHelp = PASSWORD_HELP;

  protected readonly filters = this.fb.group({
    q: [''],
    role: ['' as Role | ''],
    status: ['' as AuthUser['status'] | ''],
  });

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly result = signal<Page<AuthUser> | null>(null);
  protected readonly page = signal(0);
  protected readonly notice = signal<string | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly creating = signal(false);
  protected readonly submitted = signal(false);
  protected readonly createError = signal<string | null>(null);

  private readonly resultsHeading = viewChild<ElementRef<HTMLElement>>('resultsHeading');
  private readonly formHeading = viewChild<ElementRef<HTMLElement>>('formHeading');
  private readonly createAlert = viewChild<ElementRef<HTMLElement>>('createAlert');
  private readonly toggleButton = viewChild<ElementRef<HTMLElement>>('toggleButton');

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phoneNumber: ['', Validators.pattern(PHONE_E164)],
    initialPassword: ['', [Validators.required, passwordRules]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected providers(user: AuthUser): string {
    return user.authProviders.map((provider) => PROVIDER_LABELS[provider] ?? provider).join(', ');
  }

  protected search(): void {
    this.notice.set(null);
    this.page.set(0);
    this.load({ focusResults: true });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.load({ focusResults: true });
  }

  protected load(options: { focusResults?: boolean } = {}): void {
    const { q, role, status } = this.filters.getRawValue();
    this.loading.set(true);
    this.loadError.set(null);
    this.dispatcher
      .listUsers({
        q: q.trim() || undefined,
        role: role || undefined,
        status: status || undefined,
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
          if (options.focusResults) {
            afterNextRender(() => this.resultsHeading()?.nativeElement.focus(), { injector: this.injector });
          }
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadError.set(errorMessage(error));
        },
      });
  }

  protected toggleForm(): void {
    const open = !this.formOpen();
    this.formOpen.set(open);
    if (open) {
      this.notice.set(null);
      afterNextRender(() => this.formHeading()?.nativeElement.focus(), { injector: this.injector });
    } else {
      this.resetForm();
    }
  }

  protected error(field: NewDispatcherField): string | null {
    const control = this.form.controls[field];
    if (field === 'email' && control.hasError('taken')) {
      return 'An account with this email already exists.';
    }
    if (field === 'initialPassword' && (control.touched || this.submitted())) {
      const rule = passwordRuleMessage(control.errors);
      if (rule) {
        return rule;
      }
    }
    return fieldErrorMessage(control, this.submitted(), MESSAGES[field]);
  }

  protected createDispatcher(): void {
    this.submitted.set(true);
    this.createError.set(null);
    if (this.form.invalid || this.creating()) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.creating.set(true);
    const value = this.form.getRawValue();
    this.dispatcher
      .createDispatcher({
        fullName: value.fullName.trim(),
        email: value.email.trim(),
        phoneNumber: value.phoneNumber.trim() || undefined,
        initialPassword: value.initialPassword,
      })
      .subscribe({
        next: (user) => {
          this.creating.set(false);
          this.formOpen.set(false);
          this.resetForm();
          this.notice.set(
            `Dispatcher account created for ${user.fullName}. Give them the initial password in person or by phone; they must choose their own when they first log in.`,
          );
          this.page.set(0);
          this.load();
          afterNextRender(() => this.toggleButton()?.nativeElement.focus(), { injector: this.injector });
        },
        error: (error: unknown) => {
          this.creating.set(false);
          const problem = readProblem(error);
          if (problem.code === 'EMAIL_ALREADY_REGISTERED') {
            this.form.controls.email.setErrors({ taken: true });
            afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
            return;
          }
          if (applyServerErrors(this.form, problem)) {
            afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
            return;
          }
          this.createError.set(errorMessage(error));
          afterNextRender(() => this.createAlert()?.nativeElement.focus(), { injector: this.injector });
        },
      });
  }

  private resetForm(): void {
    this.form.reset();
    this.submitted.set(false);
    this.createError.set(null);
  }
}
