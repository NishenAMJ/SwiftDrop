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
import {
  AbstractControl,
  FormArray,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';

import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import {
  applyServerErrors,
  fieldErrorMessage,
  focusFirstInvalid,
} from '../../../shared/forms/form-errors';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  WEBHOOK_EVENTS,
  WebhookSubscription,
  WebhookSubscriptionWithSecret,
  eventLabel,
  subscriptionBadge,
} from '../webhook.models';
import { WebhookService } from '../webhook.service';

const ERRORS: Record<string, string> = {
  MERCHANT_NOT_APPROVED: 'Webhooks are available once your merchant account is approved.',
  FORBIDDEN: 'Only an approved merchant can manage webhooks.',
  INVALID_EVENT_TYPES: 'Choose at least one event to receive.',
  SUBSCRIPTION_NOT_FOUND: 'That webhook no longer exists. The list has been refreshed.',
  CONFLICT: 'Someone else changed this webhook at the same time. The list has been refreshed.',
};

/** Same rule as the backend: an absolute http(s) address. The server also blocks private hosts. */
const URL_PATTERN = /^https?:\/\/[^\s/?#]+[^\s]*$/i;

function atLeastOne(control: AbstractControl): ValidationErrors | null {
  const values = (control as FormArray<FormControl<boolean>>).controls.map((c) => c.value);
  return values.some(Boolean) ? null : { required: true };
}

type PendingAction = 'delete' | 'rotate';

/**
 * An approved merchant's webhooks: SwiftDrop calls the merchant's own server when one of their
 * parcels changes status. The signing secret is shown once, after creating or rotating it.
 */
@Component({
  selector: 'app-webhooks-page',
  imports: [ReactiveFormsModule, RouterLink, ColomboDatePipe, ConfirmDialog],
  templateUrl: './webhooks-page.html',
  styleUrl: './webhooks-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WebhooksPage implements OnInit {
  private readonly webhooks = inject(WebhookService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly events = WEBHOOK_EVENTS;
  protected readonly eventLabel = eventLabel;
  protected readonly badge = subscriptionBadge;

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly subscriptions = signal<WebhookSubscription[]>([]);
  protected readonly total = signal(0);

  protected readonly creating = signal(false);
  protected readonly submitted = signal(false);
  protected readonly createError = signal<string | null>(null);

  /** The secret, held only until the merchant says they've saved it. */
  protected readonly newSecret = signal<WebhookSubscriptionWithSecret | null>(null);
  protected readonly copyStatus = signal<'idle' | 'copied' | 'failed'>('idle');

  /** Row actions that don't need a dialog: one at a time, by subscription id. */
  protected readonly busyId = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly testedId = signal<string | null>(null);
  protected readonly rowError = signal<{ id: string; message: string } | null>(null);

  protected readonly pending = signal<{ action: PendingAction; subscription: WebhookSubscription } | null>(null);
  protected readonly dialogBusy = signal(false);
  protected readonly dialogError = signal<string | null>(null);

  private readonly secretHeading = viewChild<ElementRef<HTMLElement>>('secretHeading');
  private readonly createAlert = viewChild<ElementRef<HTMLElement>>('createAlert');

  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly form = this.fb.group({
    url: ['', [Validators.required, Validators.maxLength(2048), Validators.pattern(URL_PATTERN)]],
    // One checkbox per entry in WEBHOOK_EVENTS, in the same order. All ticked by default.
    events: this.fb.array(
      WEBHOOK_EVENTS.map(() => this.fb.control(true)),
      atLeastOne,
    ),
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.webhooks.list().subscribe({
      next: (page) => {
        this.subscriptions.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.loadError.set(errorMessage(error, ERRORS));
      },
    });
  }

  protected urlError(): string | null {
    return fieldErrorMessage(this.form.controls.url, this.submitted(), {
      required: 'Enter the address of your server, such as https://shop.example.com/swiftdrop.',
      pattern: 'Enter a full address that starts with https://',
    });
  }

  protected eventsError(): string | null {
    const control = this.form.controls.events;
    return control.invalid && (control.touched || this.submitted())
      ? 'Choose at least one event to receive.'
      : null;
  }

  protected create(): void {
    this.submitted.set(true);
    this.createError.set(null);
    this.notice.set(null);
    if (this.form.invalid || this.creating()) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.creating.set(true);
    const { url, events } = this.form.getRawValue();
    const eventTypes = WEBHOOK_EVENTS.filter((_, i) => events[i]).map((event) => event.value);
    this.webhooks.create({ url: url.trim(), eventTypes }).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.submitted.set(false);
        this.form.reset();
        this.showSecret(created);
        const { secret: _secret, ...listed } = created;
        this.subscriptions.update((list) => [listed, ...list]);
        this.total.update((count) => count + 1);
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

  private showSecret(subscription: WebhookSubscriptionWithSecret): void {
    this.copyStatus.set('idle');
    this.newSecret.set(subscription);
    afterNextRender(() => this.secretHeading()?.nativeElement.focus(), { injector: this.injector });
  }

  protected async copySecret(): Promise<void> {
    const secret = this.newSecret()?.secret;
    if (!secret) {
      return;
    }
    try {
      await navigator.clipboard.writeText(secret);
      this.copyStatus.set('copied');
    } catch {
      this.copyStatus.set('failed');
    }
  }

  protected selectAll(event: Event): void {
    (event.target as HTMLInputElement).select();
  }

  /** Forget the secret. It can't be shown again after this. */
  protected dismissSecret(): void {
    this.newSecret.set(null);
    this.copyStatus.set('idle');
    this.notice.set('Your webhook is ready. Use "Send test" to check your server receives it.');
  }

  protected setActive(subscription: WebhookSubscription, active: boolean): void {
    if (this.busyId()) {
      return;
    }
    this.busyId.set(subscription.id);
    this.rowError.set(null);
    this.notice.set(null);
    this.webhooks.update(subscription.id, { active }).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.replace(updated);
        this.notice.set(
          active
            ? 'Webhook resumed. New events will be sent again.'
            : 'Webhook paused. SwiftDrop won\'t send new events until you resume it.',
        );
      },
      error: (error: unknown) => this.rowFailed(subscription, error),
    });
  }

  protected sendTest(subscription: WebhookSubscription): void {
    if (this.busyId()) {
      return;
    }
    this.busyId.set(subscription.id);
    this.rowError.set(null);
    this.notice.set(null);
    this.testedId.set(null);
    this.webhooks.sendTest(subscription.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.testedId.set(subscription.id);
      },
      error: (error: unknown) => this.rowFailed(subscription, error),
    });
  }

  private rowFailed(subscription: WebhookSubscription, error: unknown): void {
    this.busyId.set(null);
    const code = readProblem(error).code;
    if (code === 'SUBSCRIPTION_NOT_FOUND' || code === 'CONFLICT') {
      this.notice.set(ERRORS[code]);
      this.load();
      return;
    }
    this.rowError.set({ id: subscription.id, message: errorMessage(error, ERRORS) });
  }

  private replace(updated: WebhookSubscription): void {
    this.subscriptions.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
  }

  protected ask(action: PendingAction, subscription: WebhookSubscription): void {
    this.dialogError.set(null);
    this.pending.set({ action, subscription });
  }

  protected cancelDialog(): void {
    this.pending.set(null);
  }

  protected confirmDialog(): void {
    const pending = this.pending();
    if (!pending || this.dialogBusy()) {
      return;
    }
    this.dialogBusy.set(true);
    this.dialogError.set(null);
    this.notice.set(null);
    const { action, subscription } = pending;
    const done = () => {
      this.dialogBusy.set(false);
      this.pending.set(null);
    };
    const failed = (error: unknown) => {
      this.dialogBusy.set(false);
      if (readProblem(error).code === 'SUBSCRIPTION_NOT_FOUND') {
        this.pending.set(null);
        this.notice.set(ERRORS['SUBSCRIPTION_NOT_FOUND']);
        this.load();
        return;
      }
      this.dialogError.set(errorMessage(error, ERRORS));
    };

    if (action === 'delete') {
      this.webhooks.remove(subscription.id).subscribe({
        next: () => {
          done();
          this.subscriptions.update((list) => list.filter((item) => item.id !== subscription.id));
          this.total.update((count) => Math.max(0, count - 1));
          this.notice.set('Webhook deleted. SwiftDrop no longer sends events to that address.');
        },
        error: failed,
      });
    } else {
      this.webhooks.rotateSecret(subscription.id).subscribe({
        next: (rotated) => {
          done();
          const { secret: _secret, ...listed } = rotated;
          this.replace(listed);
          this.showSecret(rotated);
        },
        error: failed,
      });
    }
  }

  protected dialogTitle(): string {
    const pending = this.pending();
    if (!pending) {
      return '';
    }
    return pending.action === 'delete' ? 'Delete this webhook?' : 'Create a new signing secret?';
  }
}
