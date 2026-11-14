import { AbstractControl } from '@angular/forms';

import { Problem } from '../../core/auth/auth.models';

/**
 * Shows a field's error once the user has left the field or tried to submit
 * (frontend/style.md section 8).
 */
export function shouldShowError(control: AbstractControl | null, submitted: boolean): boolean {
  return !!control && control.invalid && (control.touched || submitted);
}

/**
 * When the server's validation disagreed with ours (`VALIDATION_FAILED`), puts its messages on the
 * matching fields. Field names may be nested, such as `businessAddress.city`. Returns false when
 * nothing could be matched, so the caller shows a general message instead.
 */
export function applyServerErrors(form: AbstractControl, problem: Problem): boolean {
  if (problem.code !== 'VALIDATION_FAILED' || !problem.errors?.length) {
    return false;
  }
  let matched = false;
  for (const { field, message } of problem.errors) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ server: message });
      control.markAsTouched();
      matched = true;
    }
  }
  return matched;
}

/** The server's message for a field, if it sent one. */
export function serverError(control: AbstractControl | null): string | null {
  const value: unknown = control?.errors?.['server'];
  return typeof value === 'string' ? value : null;
}

export interface FieldMessages {
  /** For a missing value, for example "Enter your business name." */
  required: string;
  /** For a value in the wrong format, for example a phone number. */
  pattern?: string;
}

/**
 * The one message to show under a field, or null while it should stay quiet. Server messages win,
 * because they describe the rule that actually failed.
 */
export function fieldErrorMessage(
  control: AbstractControl | null,
  submitted: boolean,
  messages: FieldMessages,
): string | null {
  if (!control || !shouldShowError(control, submitted)) {
    return null;
  }
  const errors = control.errors ?? {};
  if (errors['server']) {
    return serverError(control);
  }
  if (errors['required']) {
    return messages.required;
  }
  if (errors['email']) {
    return 'Enter an email address like name@example.com.';
  }
  if (errors['minlength']) {
    return `Use at least ${errors['minlength'].requiredLength} characters.`;
  }
  if (errors['maxlength']) {
    return `Use at most ${errors['maxlength'].requiredLength} characters.`;
  }
  if (errors['pattern']) {
    return messages.pattern ?? messages.required;
  }
  return 'Check this field.';
}

/** After a failed submit, moves focus to the first field marked invalid inside `host`. */
export function focusFirstInvalid(host: HTMLElement): void {
  host.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
}
