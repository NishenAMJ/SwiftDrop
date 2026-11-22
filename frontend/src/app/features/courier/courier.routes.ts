import { inject } from '@angular/core';
import { RedirectFunction, Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Courier screens: the application (identity-merchant-service) and the courier's own deliveries
 * (delivery-operations-service).
 */
/**
 * /courier: an approved courier (whose token carries the COURIER role) starts on their deliveries; a customer
 * applying to become one starts on the application. Both pages stay reachable by their own address.
 */
export const courierHome: RedirectFunction = () => (inject(AuthService).hasRole('COURIER') ? 'deliveries' : 'application');

export const COURIER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: courierHome },
  {
    path: 'deliveries',
    title: 'My deliveries | SwiftDrop',
    canActivate: [roleGuard('COURIER')],
    loadComponent: () =>
      import('./my-deliveries-page/my-deliveries-page').then((m) => m.MyDeliveriesPage),
  },
  {
    path: 'cash-handovers',
    title: 'COD cash handovers | SwiftDrop',
    canActivate: [roleGuard('COURIER')],
    loadComponent: () =>
      import('../payments/courier-cash-page/courier-cash-page').then((m) => m.CourierCashPage),
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
