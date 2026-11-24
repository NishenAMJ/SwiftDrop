import { InjectionToken } from '@angular/core';

/** COD-only release. Enable only alongside the verified backend/provider deployment. */
export const ONLINE_PAYMENTS_ENABLED = new InjectionToken<boolean>('ONLINE_PAYMENTS_ENABLED', {
  providedIn: 'root',
  factory: () => false,
});
