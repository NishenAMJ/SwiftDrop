import { HttpErrorResponse } from '@angular/common/http';

import { Problem } from './auth.models';

/**
 * Turns an API error into text for people. Known codes get our own wording; anything else gets a
 * general message. Raw server text is never shown (frontend/style.md section 8).
 */
export function readProblem(error: unknown): Problem {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as Problem;
  }
  return {};
}

export function errorMessage(error: unknown, messages: Record<string, string> = {}): string {
  if (!(error instanceof HttpErrorResponse)) {
    return GENERIC;
  }
  if (error.status === 0) {
    return "Can't reach SwiftDrop. Check your connection and try again.";
  }
  if (error.status === 429) {
    const seconds = Number(error.headers.get('Retry-After'));
    return seconds > 0
      ? `Too many attempts. Try again in ${seconds} ${seconds === 1 ? 'second' : 'seconds'}.`
      : 'Too many attempts. Try again in a moment.';
  }
  const code = readProblem(error).code;
  if (code && messages[code]) {
    return messages[code];
  }
  return error.status >= 500 ? 'Something went wrong on our side. Please try again.' : GENERIC;
}

const GENERIC = "That didn't work. Please try again.";
