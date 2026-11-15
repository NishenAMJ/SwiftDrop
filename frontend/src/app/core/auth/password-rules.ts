import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Identity's password rule (contracts/openapi/identity-merchant-service.yaml): 8 to 64 characters
 * with at least one letter and one digit. Empty values are left to `Validators.required`.
 */
export function passwordRules(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value ?? '';
  if (!value) {
    return null;
  }
  const length = [...value].length;
  if (length < 8 || length > 64) {
    return { passwordLength: true };
  }
  if (!/\p{L}/u.test(value) || !/\p{N}/u.test(value)) {
    return { passwordMix: true };
  }
  return null;
}

export const PASSWORD_HELP = '8 to 64 characters, with at least one letter and one number.';

/** Message for the errors `passwordRules` reports, or null for other errors. */
export function passwordRuleMessage(errors: ValidationErrors | null | undefined): string | null {
  if (errors?.['passwordLength']) {
    return 'Use 8 to 64 characters.';
  }
  if (errors?.['passwordMix']) {
    return 'Add at least one letter and one number.';
  }
  return null;
}
