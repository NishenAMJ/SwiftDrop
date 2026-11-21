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
    // Keep the application/status page as the default entry point.
    redirectTo: 'application',
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
    path: 'api-keys',
    title: 'API keys | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./api-keys-page/api-keys-page').then((m) => m.ApiKeysPage),
  },
  {
    path: 'dashboard',
    title: 'Merchant dashboard | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./merchant-dashboard/merchant-dashboard.component').then((m) => m.MerchantDashboardComponent),
  },
  {
    path: 'create-order',
    title: 'Create order | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./create-order/create-order.component').then((m) => m.CreateOrderComponent),
  },
  {
    path: 'orders',
    title: 'Merchant orders | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./orders/merchant-orders.component').then((m) => m.MerchantOrdersComponent),
  },
  {
    path: 'payments',
    title: 'Payment history | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () =>
      import('../payments/payment-history-page/payment-history-page').then((m) => m.PaymentHistoryPage),
  },
];
