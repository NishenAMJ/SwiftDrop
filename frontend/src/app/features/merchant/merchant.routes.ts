import { Routes } from '@angular/router';

export const MERCHANT_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard',
  },
  {
    path: 'dashboard',
    title: 'Merchant Dashboard | SwiftDrop',
    loadComponent: () =>
      import('./merchant-dashboard/merchant-dashboard.component').then(
        (m) => m.MerchantDashboardComponent,
      ),
  },
  // Placeholder for future order creation page
  {
    path: 'create-order',
    title: 'Create New Order | SwiftDrop',
    loadComponent: () =>
      import('./create-order/create-order.component').then(
        (m) => m.CreateOrderComponent,
      ),
  },
  {
    path: 'orders',
    title: 'Merchant Orders | SwiftDrop',
    loadComponent: () =>
      import('./orders/merchant-orders.component').then(
        (m) => m.MerchantOrdersComponent,
      ),
  },
];
