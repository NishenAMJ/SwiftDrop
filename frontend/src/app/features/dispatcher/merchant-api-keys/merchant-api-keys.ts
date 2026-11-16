import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';

import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ApplicationStatus } from '../../../shared/models/application';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { API_KEY_STATE, ApiKey, ApiKeyState, apiKeyState } from '../../merchant/merchant.models';
import { DispatcherService } from '../dispatcher.service';

const ERRORS: Record<string, string> = {
  NOT_FOUND: 'That key or merchant no longer exists. The list has been refreshed.',
};

/**
 * One merchant's API keys, inside a merchant card on the review page. Hidden until the dispatcher
 * opens it, so the list page doesn't load every merchant's keys. A dispatcher can revoke a single
 * key (for example one the merchant reports as leaked) without suspending the whole merchant.
 */
@Component({
  selector: 'app-merchant-api-keys',
  imports: [ColomboDatePipe, ConfirmDialog],
  templateUrl: './merchant-api-keys.html',
  styleUrl: './merchant-api-keys.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantApiKeys {
  private readonly dispatcher = inject(DispatcherService);

  readonly merchantId = input.required<string>();
  readonly businessName = input.required<string>();
  readonly status = input.required<ApplicationStatus>();

  protected readonly expanded = signal(false);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly keys = signal<ApiKey[] | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly revoking = signal<ApiKey | null>(null);
  protected readonly revokeBusy = signal(false);
  protected readonly revokeError = signal<string | null>(null);

  protected toggle(): void {
    const open = !this.expanded();
    this.expanded.set(open);
    if (open) {
      this.notice.set(null);
      this.load();
    }
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.dispatcher.listMerchantApiKeys(this.merchantId()).subscribe({
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

  protected askRevoke(key: ApiKey): void {
    this.revokeError.set(null);
    this.revoking.set(key);
  }

  protected cancelRevoke(): void {
    if (!this.revokeBusy()) {
      this.revoking.set(null);
    }
  }

  protected confirmRevoke(): void {
    const key = this.revoking();
    if (!key || this.revokeBusy()) {
      return;
    }
    this.revokeBusy.set(true);
    this.revokeError.set(null);
    this.dispatcher.revokeMerchantApiKey(this.merchantId(), key.id).subscribe({
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
