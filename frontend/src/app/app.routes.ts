import { Routes } from '@angular/router';

import { authGuard, guestGuard, homeGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [homeGuard],
    loadComponent: () =>
      import('./features/auth/dashboard-page/dashboard-page').then((m) => m.DashboardPage),
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
    path: 'account',
    title: 'Your account | SwiftDrop',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/account-page/account-page').then((m) => m.AccountPage),
  },
  ...['customer', 'merchant', 'courier', 'dispatcher'].map((path) => ({
    path,
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/dashboard-page/dashboard-page').then((m) => m.DashboardPage),
  })),
  {
    path: 'choose-role',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/choose-role-page/choose-role-page').then((m) => m.ChooseRolePage),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/change-password-page/change-password-page').then(
        (m) => m.ChangePasswordPage,
      ),
  },
  {
    path: 'onboarding',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/onboarding-page/onboarding-page').then((m) => m.OnboardingPage),
  },
  { path: '**', redirectTo: '' },
];
