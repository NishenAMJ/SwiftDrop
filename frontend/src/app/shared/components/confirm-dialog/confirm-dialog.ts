import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

/** An optional text box in the dialog, such as a rejection reason or an internal note. */
export interface ConfirmDialogText {
  label: string;
  required: boolean;
  minLength?: number;
  maxLength: number;
  help?: string;
}

let nextId = 0;

/**
 * Asks before an action, explaining what will happen (frontend/style.md sections 7 and 11). Uses
 * the native modal <dialog>: it keeps focus inside, closes on Escape, and returns focus to the
 * button that opened it. The parent owns the state: it sets `open`, shows `busy` while the request
 * runs, and closes the dialog when it is done.
 */
@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialog {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly tone = input<'primary' | 'danger'>('primary');
  readonly busy = input(false);
  /** A message that the request failed; shown inside the dialog, next to the action. */
  readonly error = input<string | null>(null);
  readonly text = input<ConfirmDialogText | null>(null);

  /** Emits the trimmed text (empty when there is no text box). */
  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  protected readonly id = `sd-confirm-${nextId++}`;
  protected readonly value = signal('');
  protected readonly submitted = signal(false);

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly textError = computed(() => {
    const field = this.text();
    if (!field) {
      return null;
    }
    const length = this.value().trim().length;
    if (field.required && length === 0) {
      return `Enter a ${field.label.toLowerCase()}.`;
    }
    if (length > 0 && field.minLength && length < field.minLength) {
      return `Use at least ${field.minLength} characters.`;
    }
    if (this.value().length > field.maxLength) {
      return `Use at most ${field.maxLength} characters.`;
    }
    return null;
  });

  protected readonly showTextError = computed(() => this.submitted() && this.textError() !== null);

  protected readonly textDescribedBy = computed(() => {
    const ids = [
      this.text()?.help ? `${this.id}-help` : null,
      this.showTextError() ? `${this.id}-error` : null,
    ].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  });

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        this.value.set('');
        this.submitted.set(false);
        // jsdom (unit tests) has no showModal; the attribute is enough there.
        if (typeof element.showModal === 'function') {
          element.showModal();
        } else {
          element.setAttribute('open', '');
        }
      } else if (!this.open() && element.open) {
        if (typeof element.close === 'function') {
          element.close();
        } else {
          element.removeAttribute('open');
        }
      }
    });
  }

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLTextAreaElement).value);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.submitted.set(true);
    if (this.busy() || this.textError()) {
      return;
    }
    this.confirmed.emit(this.value().trim());
  }

  protected cancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  /** Escape key: let the parent decide, so its state and the dialog never disagree. */
  protected onNativeCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }
}
