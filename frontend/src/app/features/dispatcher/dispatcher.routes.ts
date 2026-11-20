import { Routes } from '@angular/router';
import { DispatcherLayoutComponent } from '../../core/layout/dispatcher-layout/dispatcher-layout';

/**
 * Lazy‑loaded routes for the Dispatcher feature.
 * Uses the DispatcherLayoutComponent which provides a sidebar and a router outlet.
 */
export const DISPATCHER_ROUTES: Routes = [
  {
    path: '',
    component: DispatcherLayoutComponent,
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
      // Additional dispatcher pages can be added here.
    ],
  },
  {
    path: 'payments',
    title: 'Payments and refunds | SwiftDrop',
    loadComponent: () =>
      import('../payments/payment-history-page/payment-history-page').then((m) => m.PaymentHistoryPage),
  },
];
