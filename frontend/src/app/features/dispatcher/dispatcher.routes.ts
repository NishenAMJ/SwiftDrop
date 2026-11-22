import { Routes } from '@angular/router';

/**
 * Dispatcher screens. The parent route in app.routes.ts already requires the DISPATCHER role and
 * renders these inside the role-aware app layout, whose sidebar links to each page.
 * Identity: merchant/courier approvals and users. Parcel pricing: delivery zones and pricing rules.
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
];
