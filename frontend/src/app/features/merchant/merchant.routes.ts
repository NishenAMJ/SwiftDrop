import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';

/**
 * Merchant screens from identity-merchant-service. Other teams can add merchant pages here (for
 * example orders); when they do, point the default route at the merchant's main page.
 */
export const MERCHANT_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    // Approved merchants start at their dashboard; others at the application.
    redirectTo: 'dashboard',
  },
  {
    // Customers apply here; approved and suspended merchants follow their status here.
    path: 'application',
    title: 'Merchant application | SwiftDrop',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () =>
      import('./merchant-application-page/merchant-application-page').then(
        (m) => m.MerchantApplicationPage,
      ),
  },
  {
    // Merchant Dashboard
    path: 'dashboard',
    title: 'Merchant Dashboard | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./merchant-dashboard/merchant-dashboard.component').then((m) => m.MerchantDashboardComponent),
  },
  {
    // Merchant My Bookings
    path: 'bookings',
    title: 'My Bookings | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./orders/merchant-orders.component').then((m) => m.MerchantOrdersComponent),
  },
  {
    path: 'my-bookings',
    redirectTo: 'bookings',
  },
  {
    path: 'receipt/:id',
    title: 'Booking Receipt | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () =>
      import('../customer/booking-receipt/booking-receipt.component').then(
        (m) => m.BookingReceiptComponent,
      ),
  },
  {
    path: 'create-order',
    title: 'Create Order | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () =>
      import('./create-order/create-order.component').then((m) => m.CreateOrderComponent),
  },
  {
    path: 'api-keys',
    title: 'API keys | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./api-keys-page/api-keys-page').then((m) => m.ApiKeysPage),
  },
];
