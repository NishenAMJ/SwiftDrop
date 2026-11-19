import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import {
  FieldMessages,
  applyServerErrors,
  fieldErrorMessage,
  focusFirstInvalid,
} from '../../../shared/forms/form-errors';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  API_KEY_STATE,
  ApiKey,
  ApiKeyState,
  CreatedApiKey,
  MAX_ACTIVE_API_KEYS,
  apiKeyState,
} from '../merchant.models';
import { MerchantService } from '../merchant.service';

const MESSAGES: Record<'name' | 'expiresOn', FieldMessages> = {
  name: { required: 'Give the key a name, such as the system that will use it.' },
  expiresOn: { required: '' },
};

const ERRORS: Record<string, string> = {
  API_KEY_LIMIT_REACHED: `You already have ${MAX_ACTIVE_API_KEYS} active keys. Revoke one first.`,
  MERCHANT_NOT_ACTIVE: "Your merchant account isn't active, so you can't manage API keys.",
  FORBIDDEN: 'Only an approved merchant can manage API keys.',
  NOT_FOUND: 'That key no longer exists. The list has been refreshed.',
};

/** Today's date in Sri Lanka, as YYYY-MM-DD (the format of <input type="date">). */
export function colomboToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(now);
}

/** A key set to expire "on" a date works until the end of that day, Sri Lanka time. */
export function endOfColomboDay(date: string): string {
  return new Date(`${date}T23:59:59+05:30`).toISOString();
}

function notInPast(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value < colomboToday() ? { past: true } : null;
}

/**
 * An approved merchant's API keys, for connecting their own systems. The full key is shown once,
 * right after it is created; SwiftDrop only keeps a hash. Revoking a key stops it at once.
 */
@Component({
  selector: 'app-api-keys-page',
  imports: [ReactiveFormsModule, ColomboDatePipe, ConfirmDialog],
  templateUrl: './api-keys-page.html',
  styleUrl: './api-keys-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiKeysPage implements OnInit {
  private readonly merchants = inject(MerchantService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly maxKeys = MAX_ACTIVE_API_KEYS;
  protected readonly today = colomboToday();

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly keys = signal<ApiKey[]>([]);

  protected readonly creating = signal(false);
  protected readonly submitted = signal(false);
  protected readonly createError = signal<string | null>(null);
  /** The new key, held only until the merchant says they've saved it. */
  protected readonly newKey = signal<CreatedApiKey | null>(null);
  protected readonly copyStatus = signal<'idle' | 'copied' | 'failed'>('idle');

  protected readonly revoking = signal<ApiKey | null>(null);
  protected readonly revokeBusy = signal(false);
  protected readonly revokeError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly activeCount = computed(
    () => this.keys().filter((key) => this.stateOf(key) === 'active').length,
  );
  protected readonly limitReached = computed(() => this.activeCount() >= MAX_ACTIVE_API_KEYS);

  private readonly newKeyHeading = viewChild<ElementRef<HTMLElement>>('newKeyHeading');
  private readonly createAlert = viewChild<ElementRef<HTMLElement>>('createAlert');

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(60), Validators.pattern(/\S/)]],
    expiresOn: ['', notInPast],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.merchants.listApiKeys().subscribe({
      next: (keys) => {
        this.keys.set(keys);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.loadError.set(errorMessage(error, ERRORS));
      },
    });
  }

  protected stateOf(key: ApiKey): ApiKeyState {
    return apiKeyState(key);
  }

  protected stateLabel(key: ApiKey): string {
    return API_KEY_STATE[this.stateOf(key)].label;
  }

  protected stateBadge(key: ApiKey): string {
    return API_KEY_STATE[this.stateOf(key)].badge;
  }

  protected error(field: 'name' | 'expiresOn'): string | null {
    const control = this.form.controls[field];
    if (field === 'expiresOn' && control.hasError('past') && (control.touched || this.submitted())) {
      return 'Choose today or a later date.';
    }
    return fieldErrorMessage(control, this.submitted(), MESSAGES[field]);
  }

  protected create(): void {
    this.submitted.set(true);
    this.createError.set(null);
    this.notice.set(null);
    if (this.form.invalid || this.creating() || this.limitReached()) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.creating.set(true);
    const { name, expiresOn } = this.form.getRawValue();
    this.merchants
      .createApiKey({ name: name.trim(), expiresAt: expiresOn ? endOfColomboDay(expiresOn) : undefined })
      .subscribe({
        next: (created) => {
          this.creating.set(false);
          this.submitted.set(false);
          this.form.reset();
          this.copyStatus.set('idle');
          this.newKey.set(created);
          // The list keeps only the safe part; the full key lives in `newKey` alone.
          const { key: _secret, ...listed } = created;
          this.keys.update((keys) => [listed, ...keys]);
          afterNextRender(() => this.newKeyHeading()?.nativeElement.focus(), { injector: this.injector });
        },
        error: (error: unknown) => {
          this.creating.set(false);
          if (applyServerErrors(this.form, readProblem(error))) {
            afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
            return;
          }
          this.createError.set(errorMessage(error, ERRORS));
          afterNextRender(() => this.createAlert()?.nativeElement.focus(), { injector: this.injector });
        },
      });
  }

  protected async copyKey(): Promise<void> {
    const key = this.newKey()?.key;
    if (!key) {
      return;
    }
    try {
      await navigator.clipboard.writeText(key);
      this.copyStatus.set('copied');
    } catch {
      this.copyStatus.set('failed');
    }
  }

  protected selectAll(event: Event): void {
    (event.target as HTMLInputElement).select();
  }

  /** Forget the full key. It can't be shown again after this. */
  protected dismissNewKey(): void {
    const name = this.newKey()?.name;
    this.newKey.set(null);
    this.copyStatus.set('idle');
    this.notice.set(name ? `Key "${name}" is ready to use.` : null);
  }

  protected askRevoke(key: ApiKey): void {
    this.revokeError.set(null);
    this.revoking.set(key);
  }

  protected cancelRevoke(): void {
    this.revoking.set(null);
  }

  protected confirmRevoke(): void {
    const key = this.revoking();
    if (!key || this.revokeBusy()) {
      return;
    }
    this.revokeBusy.set(true);
    this.revokeError.set(null);
    this.merchants.revokeApiKey(key.id).subscribe({
      next: () => {
        this.revokeBusy.set(false);
        this.revoking.set(null);
        this.notice.set(`Key "${key.name}" is revoked. Systems using it can no longer connect.`);
        this.load();
      },
      error: (error: unknown) => {
        this.revokeBusy.set(false);
        if (readProblem(error).code === 'NOT_FOUND') {
          this.revoking.set(null);
          this.notice.set(ERRORS['NOT_FOUND']);
          this.load();
          return;
        }
        this.revokeError.set(errorMessage(error, ERRORS));
      },
    });
  }
}
