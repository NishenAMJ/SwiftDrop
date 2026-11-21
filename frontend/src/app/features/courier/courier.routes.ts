import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';

/**
 * Courier screens: the application (identity-merchant-service) and the courier's own deliveries
 * (delivery-operations-service).
 */
export const COURIER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'application' },
  {
    path: 'deliveries',
    title: 'My deliveries | SwiftDrop',
    canActivate: [roleGuard('COURIER')],
    loadComponent: () =>
      import('./my-deliveries-page/my-deliveries-page').then((m) => m.MyDeliveriesPage),
  },
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
