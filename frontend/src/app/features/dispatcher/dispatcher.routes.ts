import { Routes } from '@angular/router';
import { AppLayout } from '../../core/layout/app-layout/app-layout';

/**
 * Lazy‑loaded routes for the Dispatcher feature.
 * Uses the shared role-aware navigation and router outlet.
 */
export const DISPATCHER_ROUTES: Routes = [
  {
    path: '',
    component: AppLayout,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'delivery-zones',
      },
      {
        path: 'delivery-zones',
        title: 'Delivery Zones | SwiftDrop',
        loadComponent: () =>
          import('./delivery-zones/delivery-zones.component').then((m) => m.DeliveryZonesComponent),
      },
      {
        path: 'pricing-rules',
        title: 'Pricing Rules | SwiftDrop',
        loadComponent: () =>
          import('./pricing-rules/pricing-rules.component').then((m) => m.PricingRulesComponent),
      },
      {
        path: 'merchants',
        title: 'Merchant applications | SwiftDrop',
        loadComponent: () => import('./merchant-review-page/merchant-review-page').then((m) => m.MerchantReviewPage),
      },
      {
        path: 'couriers',
        title: 'Courier applications | SwiftDrop',
        loadComponent: () => import('./courier-review-page/courier-review-page').then((m) => m.CourierReviewPage),
      },
      {
        path: 'users',
        title: 'Users | SwiftDrop',
        loadComponent: () => import('./users-page/users-page').then((m) => m.UsersPage),
      },
      {
        path: 'payments',
        title: 'Payments and refunds | SwiftDrop',
        loadComponent: () => import('../payments/payment-history-page/payment-history-page').then((m) => m.PaymentHistoryPage),
      },
    ],
  },
];
