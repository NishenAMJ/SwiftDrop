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
        title: 'Dispatcher overview | SwiftDrop',
        loadComponent: () =>
          import('./dispatcher-overview/dispatcher-overview').then((m) => m.DispatcherOverview),
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
        path: 'courier-categories',
        title: 'Courier categories | SwiftDrop',
        loadComponent: () =>
          import('./courier-categories-page/courier-categories-page').then(
            (m) => m.CourierCategoriesPage,
          ),
      },
      {
        path: 'courier-fleet',
        title: 'Couriers | SwiftDrop',
        loadComponent: () =>
          import('./courier-fleet-page/courier-fleet-page').then((m) => m.CourierFleetPage),
      },
      {
        path: 'assignments',
        title: 'Courier assignments | SwiftDrop',
        loadComponent: () =>
          import('./assignments-page/assignments-page').then((m) => m.AssignmentsPage),
      },
      {
        path: 'delivery-issues',
        title: 'Delivery issues | SwiftDrop',
        loadComponent: () =>
          import('./delivery-issues-page/delivery-issues-page').then((m) => m.DeliveryIssuesPage),
      },
      {
        path: 'merchants',
        title: 'Merchant applications | SwiftDrop',
        loadComponent: () =>
          import('./merchant-review-page/merchant-review-page').then((m) => m.MerchantReviewPage),
      },
      {
        path: 'couriers',
        title: 'Courier applications | SwiftDrop',
        loadComponent: () =>
          import('./courier-review-page/courier-review-page').then((m) => m.CourierReviewPage),
      },
      {
        path: 'users',
        title: 'Users | SwiftDrop',
        loadComponent: () => import('./users-page/users-page').then((m) => m.UsersPage),
      },
      {
        path: 'payments',
        title: 'Payments and refunds | SwiftDrop',
        loadComponent: () =>
          import('../payments/payment-history-page/payment-history-page').then(
            (m) => m.PaymentHistoryPage,
          ),
      },
      {
        path: 'cash-reconciliation',
        title: 'Cash reconciliation | SwiftDrop',
        loadComponent: () =>
          import('../payments/cash-reconciliation-page/cash-reconciliation-page').then(
            (m) => m.CashReconciliationPage,
          ),
      },
      {
        path: 'merchant-finance',
        title: 'Merchant finance operations | SwiftDrop',
        loadComponent: () =>
          import('../payments/settlement-operations-page/settlement-operations-page').then(
            (m) => m.SettlementOperationsPage,
          ),
      },
    ],
  },
];
