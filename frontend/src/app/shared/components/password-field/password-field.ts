import {
  AfterContentInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  signal,
} from '@angular/core';

/**
 * Wraps one password input and adds a Show/Hide button next to it, so people can check what they
 * typed. The input keeps its own label, errors and autocomplete; this only switches its type.
 *
 * ```html
 * <app-password-field>
 *   <input id="login-password" class="sd-input" type="password" formControlName="password" />
 * </app-password-field>
 * ```
 */
@Component({
  selector: 'app-password-field',
  template: `
    <ng-content />
    <button
      class="sd-button sd-button--secondary password-field__toggle"
      type="button"
      [attr.aria-controls]="inputId()"
      (click)="toggle()"
    >
      {{ visible() ? 'Hide' : 'Show' }}<span class="sd-visually-hidden"> {{ label() }}</span>
    </button>
  `,
  styleUrl: './password-field.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordField implements AfterContentInit {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Read after "Show"/"Hide" by screen readers, e.g. "new password". */
  readonly label = input('password');

  protected readonly visible = signal(false);
  protected readonly inputId = signal<string | null>(null);

  ngAfterContentInit(): void {
    this.inputId.set(this.input()?.id || null);
  }

  protected toggle(): void {
    const field = this.input();
    if (!field) {
      return;
    }
    const show = !this.visible();
    field.type = show ? 'text' : 'password';
    this.visible.set(show);
  }

  private input(): HTMLInputElement | null {
    return this.host.nativeElement.querySelector('input');
  }
}
