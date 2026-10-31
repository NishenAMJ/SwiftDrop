import { HOME_AFTER_LOGIN, safeReturnUrl } from './auth.guards';

describe('safeReturnUrl', () => {
  it('keeps paths inside the app', () => {
    expect(safeReturnUrl('/account')).toBe('/account');
    expect(safeReturnUrl('/merchant/orders?page=2')).toBe('/merchant/orders?page=2');
  });

  it('refuses anything that could leave the app', () => {
    for (const value of ['https://evil.example', '//evil.example', 'evil.example', '', null, undefined]) {
      expect(safeReturnUrl(value)).toBe(HOME_AFTER_LOGIN);
    }
  });
});
