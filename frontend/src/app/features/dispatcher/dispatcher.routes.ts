import { Routes } from '@angular/router';

/**
<<<<<<< HEAD
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
];

// Import the layout component for type safety (stand‑alone component).
import { DispatcherLayoutComponent } from '../../core/layout/dispatcher-layout/dispatcher-layout';
=======
 * Dispatcher screens from identity-merchant-service. The parent route in app.routes.ts already
 * requires the DISPATCHER role. Other teams' dispatcher pages (assignments, issues) can be added here.
 */
export const DISPATCHER_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'merchants' },
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
];
>>>>>>> 35602236d36ad207e8a1afb037f49083e42ec995
