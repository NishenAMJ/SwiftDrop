import { Routes } from '@angular/router';

/**
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
