import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';

/** Webhook screens from notification-webhook-service, inside the signed-in app layout. */
export const NOTIFICATION_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Webhooks | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () => import('./webhooks-page/webhooks-page').then((m) => m.WebhooksPage),
  },
  {
    // Before ':id', so "failed" is never read as a subscription id.
    path: 'failed',
    title: 'Failed webhooks | SwiftDrop',
    canActivate: [roleGuard('DISPATCHER')],
    loadComponent: () =>
      import('./failed-webhooks-page/failed-webhooks-page').then((m) => m.FailedWebhooksPage),
  },
  {
    path: ':id',
    title: 'Webhook deliveries | SwiftDrop',
    canActivate: [roleGuard('MERCHANT')],
    loadComponent: () =>
      import('./webhook-deliveries-page/webhook-deliveries-page').then(
        (m) => m.WebhookDeliveriesPage,
      ),
  },
];
