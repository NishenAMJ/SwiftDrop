import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';

/**
 * Courier screens from identity-merchant-service. Delivery work (assignments, proofs) belongs to
 * delivery-operations-service; its pages can be added here, and the default route pointed at them.
 */
export const COURIER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'application' },
  {
    // Customers apply here; approved and suspended couriers follow their status here.
    path: 'application',
    title: 'Courier application | SwiftDrop',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () =>
      import('./courier-application-page/courier-application-page').then(
        (m) => m.CourierApplicationPage,
      ),
  },
];
