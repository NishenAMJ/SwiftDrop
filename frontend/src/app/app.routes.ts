import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  { path: '', pathMatch: 'full', redirectTo: 'customer' },
  {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  {
    // Login, register and the Google callback share the Arctic Frost auth layout.
    path: '',
    loadComponent: () => import('./core/layout/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  {
        path: 'login',
        title: 'Log in | SwiftDrop',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/login-page/login-page').then((m) => m.LoginPage),
      },
      {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  {
        path: 'register',
        title: 'Create account | SwiftDrop',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/register-page/register-page').then((m) => m.RegisterPage),
      },
      {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
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
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  {
    path: 'customer',
    loadComponent: () =>
      import('./core/layout/customer-layout/customer-layout').then((m) => m.CustomerLayout),
    loadChildren: () =>
      import('./features/customer/customer.routes').then((m) => m.CUSTOMER_ROUTES),
  },
  {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  {
    path: 'account',
    title: 'Your account | SwiftDrop',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/account-page/account-page').then((m) => m.AccountPage),
  },
  {
    path: 'dispatcher',
    loadChildren: () =>
      import('./features/dispatcher/dispatcher.routes').then((m) => m.DISPATCHER_ROUTES),
  },
  { path: '**', redirectTo: 'account' },
];
