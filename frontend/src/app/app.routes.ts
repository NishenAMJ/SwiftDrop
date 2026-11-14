import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  // Signed-out visitors see the landing page; signed-in users go to their own role's page.
  {
    path: '',
    pathMatch: 'full',
    title: 'SwiftDrop | Parcel delivery',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/landing/landing-page/landing-page').then((m) => m.LandingPage),
  },
  {
    // Login, register and the Google callback share the Arctic Frost auth layout.
    path: '',
    loadComponent: () => import('./core/layout/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      {
        path: 'login',
        title: 'Log in | SwiftDrop',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/login-page/login-page').then((m) => m.LoginPage),
      },
      {
        path: 'register',
        title: 'Create account | SwiftDrop',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/register-page/register-page').then((m) => m.RegisterPage),
      },
      {
        path: 'auth/callback',
        title: 'Logging in | SwiftDrop',
        loadComponent: () =>
          import('./features/auth/auth-callback-page/auth-callback-page').then(
            (m) => m.AuthCallbackPage,
          ),
      },
    ],
  },
  {
    // Customer portal (parcel-pricing team), with its own layout. Dispatcher accounts have no
    // CUSTOMER role, so they are sent to their account page instead.
    path: 'customer',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () =>
      import('./core/layout/customer-layout/customer-layout').then((m) => m.CustomerLayout),
    loadChildren: () =>
      import('./features/customer/customer.routes').then((m) => m.CUSTOMER_ROUTES),
  },
  {
    path: 'tracking',
    loadChildren: () =>
      import('./features/tracking/tracking.routes').then((m) => m.TRACKING_ROUTES),
  },
  {
    // Signed-in screens in the role-aware app layout. Every child also checks its own role.
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/app-layout/app-layout').then((m) => m.AppLayout),
    children: [
      {
        path: 'account',
        title: 'Your account | SwiftDrop',
        loadComponent: () =>
          import('./features/auth/account-page/account-page').then((m) => m.AccountPage),
      },
      {
        path: 'merchant',
        loadChildren: () =>
          import('./features/merchant/merchant.routes').then((m) => m.MERCHANT_ROUTES),
      },
      {
        path: 'courier',
        loadChildren: () =>
          import('./features/courier/courier.routes').then((m) => m.COURIER_ROUTES),
      },
      {
        path: 'dispatcher',
        canActivate: [roleGuard('DISPATCHER')],
        loadChildren: () =>
          import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: 'account' },
];
