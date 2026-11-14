import { signal } from '@angular/core';
import { Observable } from 'rxjs';

import { errorMessage, readProblem } from '../../core/auth/problem-messages';
import { ConfirmDialogText } from '../../shared/components/confirm-dialog/confirm-dialog';
import { ApplicationDecision } from '../../shared/models/application';
import { DecisionBody } from './dispatcher.service';

export type ApplicationKind = 'merchant' | 'courier';

interface DecisionCopy {
  /** Button text in the list. */
  action: string;
  confirm: string;
  tone: 'primary' | 'danger';
  text: ConfirmDialogText;
  /** What will happen, shown in the dialog before confirming. */
  explain: Record<ApplicationKind, string>;
  /** Shown after it worked, with the applicant's name. */
  done: string;
}

const REASON = (who: string): ConfirmDialogText => ({
  label: 'Reason',
  required: true,
  minLength: 3,
  maxLength: 500,
  help: `${who} sees this. Be specific so they know what to do next.`,
});

const NOTE: ConfirmDialogText = {
  label: 'Internal note',
  required: false,
  maxLength: 500,
  help: 'Only dispatchers see this.',
};

/** Wording for each decision (frontend/style.md section 11: explain before confirming). */
export const DECISION_COPY: Record<ApplicationDecision, DecisionCopy> = {
  approve: {
    action: 'Approve',
    confirm: 'Approve',
    tone: 'primary',
    text: NOTE,
    explain: {
      merchant:
        'The merchant account opens. They get merchant access the next time their session refreshes (within 15 minutes) and can create API keys.',
      courier:
        'They get courier access the next time their session refreshes (within 15 minutes) and can be given deliveries.',
    },
    done: 'approved',
  },
  reject: {
    action: 'Reject',
    confirm: 'Reject application',
    tone: 'danger',
    text: REASON('The applicant'),
    explain: {
      merchant: 'The application is closed. The applicant can correct it and apply again.',
      courier: 'The application is closed. The applicant can correct it and apply again.',
    },
    done: 'rejected',
  },
  suspend: {
    action: 'Suspend',
    confirm: 'Suspend',
    tone: 'danger',
    text: REASON('The account holder'),
    explain: {
      merchant:
        'Their API keys stop working at once, and they lose merchant access within 15 minutes. You can reactivate them later.',
      courier: 'They lose courier access within 15 minutes. You can reactivate them later.',
    },
    done: 'suspended',
  },
  reactivate: {
    action: 'Reactivate',
    confirm: 'Reactivate',
    tone: 'primary',
    text: NOTE,
    explain: {
      merchant:
        'Merchant access returns within 15 minutes. API keys they revoked stay revoked; others work again.',
      courier: 'Courier access returns within 15 minutes.',
    },
    done: 'reactivated',
  },
};

const ERRORS: Record<string, string> = {
  VALIDATION_FAILED: 'Check the reason and try again.',
};

/** Someone else decided first, or the record changed: the list has to be reloaded. */
const STALE_CODES = new Set(['INVALID_STATUS_TRANSITION', 'CONFLICT', 'NOT_FOUND']);

export interface PendingDecision<T> {
  target: T;
  name: string;
  decision: ApplicationDecision;
}

/**
 * Confirm-then-send for one dispatcher decision at a time. The page shows the dialog from
 * `pending`, `busy` and `error`, and gets told when it is done or the list is out of date.
 */
export class DecisionFlow<T> {
  readonly pending = signal<PendingDecision<T> | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  constructor(
    private readonly send: (target: T, decision: ApplicationDecision, body: DecisionBody) => Observable<unknown>,
    private readonly finished: (outcome: { message: string; stale: boolean }) => void,
  ) {}

  ask(target: T, name: string, decision: ApplicationDecision): void {
    this.error.set(null);
    this.pending.set({ target, name, decision });
  }

  cancel(): void {
    if (!this.busy()) {
      this.pending.set(null);
    }
  }

  confirm(text: string): void {
    const pending = this.pending();
    if (!pending || this.busy()) {
      return;
    }
    const body: DecisionBody = DECISION_COPY[pending.decision].text.required
      ? { reason: text }
      : text
        ? { note: text }
        : {};
    this.busy.set(true);
    this.error.set(null);
    this.send(pending.target, pending.decision, body).subscribe({
      next: () => {
        this.busy.set(false);
        this.pending.set(null);
        this.finished({ message: `${pending.name}: ${DECISION_COPY[pending.decision].done}.`, stale: false });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (STALE_CODES.has(readProblem(error).code ?? '')) {
          this.pending.set(null);
          this.finished({
            message: `${pending.name} was changed by someone else, so nothing was done. The list now shows the current status.`,
            stale: true,
          });
          return;
        }
        this.error.set(errorMessage(error, ERRORS));
      },
    });
  }
}
