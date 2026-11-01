import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { errorMessage } from './problem-messages';

function failure(status: number, body: object | null = null, headers?: Record<string, string>) {
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

describe('errorMessage', () => {
  it('uses our wording for known codes, never the server text', () => {
    const error = failure(401, { code: 'INVALID_CREDENTIALS', detail: 'raw server text' });
    expect(errorMessage(error, { INVALID_CREDENTIALS: 'Email or password is incorrect.' })).toBe(
      'Email or password is incorrect.',
    );
    expect(errorMessage(error)).not.toContain('raw server text');
  });

  it('says how long to wait when rate limited', () => {
    expect(errorMessage(failure(429, { code: 'RATE_LIMITED' }, { 'Retry-After': '42' }))).toBe(
      'Too many attempts. Try again in 42 seconds.',
    );
    expect(errorMessage(failure(429))).toBe('Too many attempts. Try again in a moment.');
  });

  it('explains network and server failures', () => {
    expect(errorMessage(failure(0))).toContain("Can't reach SwiftDrop");
    expect(errorMessage(failure(503))).toContain('on our side');
  });
});
