import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Merchant screens from identity-merchant-service. Other teams can add merchant pages here (for
 * example orders); when they do, point the default route at the merchant's main page.
 */
export const MERCHANT_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    // Approved merchants start at their keys; everyone else at the application.
    redirectTo: () =>
      inject(AuthService).hasRole('MERCHANT') ? '/merchant/api-keys' : '/merchant/application',
  },
  {
    // Customers apply here; approved and suspended merchants follow their status here.
    path: 'application',
    title: 'Merchant application | SwiftDrop',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () =>
      import('./merchant-application-page/merchant-application-page').then(
        (m) => m.MerchantApplicationPage,
      ),
  },
  {
    path: 'api-keys',
    title: 'API keys | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./api-keys-page/api-keys-page').then((m) => m.ApiKeysPage),
  },
];
